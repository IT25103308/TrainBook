package com.trainbooking.it25100228.service;
import com.trainbooking.it25100228.dto.ValidationResultDto;
import com.trainbooking.it25100228.model.BoardingLog;
import com.trainbooking.it25100228.repository.BoardingLogRepository;
import com.trainbooking.it25100977.model.*;
import com.trainbooking.it25100977.repository.TicketRepository;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25103308.service.SeatSelection;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25102327.service.ServicePlan;
import com.trainbooking.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class TicketValidationService {
 private final TicketRepository tickets;
 private final BoardingLogRepository logs;
 private final BookingRepository bookings;
 private final ScheduleRepository schedules;
 private final EntityManager em;
 /** Booking -> schedule -> ticket locks match payment/rebooking order. All eligibility is reread after waiting. */
 @Transactional(isolation=Isolation.READ_COMMITTED)
 public ValidationResultDto validateAndBoardTicket(String input,Long expectedSchedule,LocalDate expectedDate,String actor){
  String code=normalize(input);
  if(code==null || expectedSchedule==null || expectedSchedule<1 || expectedDate==null)return result(null,input,BoardingLog.ScanResult.MALFORMED,"Enter a valid ticket reference and select its departure/date.",actor,expectedSchedule,expectedDate);
  Ticket initial=tickets.findByTicketNumberIgnoreCase(code).orElse(null);
  if(initial==null)return result(null,code,BoardingLog.ScanResult.NOT_FOUND,"No issued ticket matches this reference.",actor,expectedSchedule,expectedDate);
  Booking booking=bookings.findByIdForUpdate(initial.getBooking().getId()).orElseThrow(()->new ResourceNotFoundException("Reservation not found."));refresh(booking);
  var schedule=schedules.findByIdForUpdate(booking.getSchedule().getId()).orElseThrow(()->new ResourceNotFoundException("Service not found."));refresh(schedule);if(em!=null)refresh(schedule.getTrain());booking.setSchedule(schedule);
  Ticket ticket=tickets.findByIdForUpdate(initial.getId()).orElseThrow(()->new ResourceNotFoundException("Ticket not found."));refresh(ticket);ticket.setBooking(booking);if(ticket.getPayment()!=null)refresh(ticket.getPayment());
  if(!Objects.equals(schedule.getId(),expectedSchedule))return result(ticket,code,BoardingLog.ScanResult.WRONG_SERVICE,"This ticket belongs to a different departure. Select the correct service.",actor,expectedSchedule,expectedDate);
  if(!Objects.equals(booking.getTravelDate(),expectedDate))return result(ticket,code,BoardingLog.ScanResult.WRONG_DATE,"This ticket is for another travel date.",actor,expectedSchedule,expectedDate);
  if(Boolean.TRUE.equals(ticket.getIsBoarded()))return result(ticket,code,BoardingLog.ScanResult.DUPLICATE,"This ticket has already been boarded. Do not admit it again.",actor,expectedSchedule,expectedDate);
  var failure=eligibility(ticket,LocalDateTime.now());if(failure!=null)return result(ticket,code,failure.code,failure.message,actor,expectedSchedule,expectedDate);
  ticket.setIsBoarded(true);ticket.setBoardedAt(LocalDateTime.now());tickets.save(ticket);
  return result(ticket,code,BoardingLog.ScanResult.VALID,"Boarding recorded for all "+booking.getNumberOfSeats()+" passengers on this ticket.",actor,expectedSchedule,expectedDate);
 }
 private record Failure(BoardingLog.ScanResult code,String message){}
 private Failure eligibility(Ticket ticket,LocalDateTime now){
  Booking b=ticket.getBooking();var pay=ticket.getPayment();
  if(b==null || b.getStatus()==Booking.BookingStatus.CANCELLED)return new Failure(BoardingLog.ScanResult.CANCELLED,"This reservation was cancelled.");
  if(b.getStatus()!=Booking.BookingStatus.CONFIRMED || pay==null || pay.getStatus()!=Payment.PaymentStatus.COMPLETED || pay.getBooking()==null || !Objects.equals(pay.getBooking().getId(),b.getId()) || pay.getAmount()==null || b.getTotalAmount()==null || pay.getAmount().signum()<=0 || pay.getAmount().compareTo(b.getTotalAmount())!=0)return new Failure(BoardingLog.ScanResult.UNPAID,"A matching completed payment is required.");
  if(b.getSchedule()==null || b.getTravelDate()==null)return new Failure(BoardingLog.ScanResult.UNAVAILABLE,"The saved service needs staff review.");
  var plan=ServicePlan.resolve(b.getSchedule(),b.getTravelDate());var train=b.getSchedule().getTrain();
  if(!plan.runs() || plan.maintenance() || plan.isCancelled() || !Boolean.TRUE.equals(b.getSchedule().getIsActive()) || train==null || train.getStatus()==null || Set.of("MAINTENANCE","INACTIVE").contains(train.getStatus().name()))return new Failure(BoardingLog.ScanResult.UNAVAILABLE,"This departure is unavailable. Check the dated service status.");
  if(plan.departure()==null || plan.arrival()==null)return new Failure(BoardingLog.ScanResult.UNAVAILABLE,"Service timing is unavailable.");
  LocalTime departure=b.getDepartureTimeSnapshot()==null?plan.departure():b.getDepartureTimeSnapshot(),arrival=b.getArrivalTimeSnapshot()==null?plan.arrival():b.getArrivalTimeSnapshot();int offset=b.getArrivalDayOffsetSnapshot()==null?plan.arrivalDayOffset():b.getArrivalDayOffsetSnapshot();
  if(now.isBefore(b.getTravelDate().atTime(departure).minusHours(2)) || now.isAfter(b.getTravelDate().plusDays(offset).atTime(arrival).plusMinutes(plan.delayMinutes()).plusHours(1)))return new Failure(BoardingLog.ScanResult.OUTSIDE_WINDOW,"Boarding opens two hours before departure and closes one hour after its estimated arrival.");
  try{int capacity=b.getSeatClass()==Booking.SeatClass.FIRST?train.getFirstClassSeats():train.getSecondClassSeats();if(b.getSeatClass()==null || b.getNumberOfSeats()==null || b.getNumberOfSeats()<1 || b.getNumberOfSeats()>6 || SeatSelection.parseStored(b.getSeatNumbers(),b.getSeatClass(),capacity).size()!=b.getNumberOfSeats())throw new IllegalArgumentException();}
  catch(Exception invalid){return new Failure(BoardingLog.ScanResult.INVALID_ALLOCATION,"The seat allocation needs staff review.");}return null;
 }
 private ValidationResultDto result(Ticket t,String code,BoardingLog.ScanResult state,String message,String actor,Long selected,LocalDate date){
  String safe=code==null?"(empty)":code.replaceAll("[\\p{Cntrl}<>]","?");if(safe.length()>100)safe=safe.substring(0,100);
  logs.save(BoardingLog.builder().ticket(t).scannedTicketNumber(safe).scanResult(state).detail(message).operatorEmail(actor).stationCode(t==null?"Not recorded":t.getOrigin()).scheduleId(selected).serviceDate(date).passengerCount(state==BoardingLog.ScanResult.VALID?t.getBooking().getNumberOfSeats():0).build());
  var result=ValidationResultDto.builder().valid(state==BoardingLog.ScanResult.VALID).message(message);
  if(t!=null)result.ticketNumber(t.getTicketNumber()).passengerName(t.getPassengerName()).trainName(t.getTrainName()).origin(t.getOrigin()).destination(t.getDestination()).seatClass(t.getSeatClass()).seatNumbers(t.getSeatNumbers()).travelDate(t.getTravelDate());return result.build();
 }
 public static String normalize(String input){
  if(input==null || input.length()>256)return null;String text=input.strip();
  // Issued QR data is an exact ticket reference; do not interpret numeric database IDs or arbitrary JSON.
  if(text.length()>2 && text.startsWith("\"") && text.endsWith("\""))text=text.substring(1,text.length()-1).strip();
  return text.matches("(?i)TKT-[A-Z0-9]+(?:-[A-Z0-9]+)*") && text.length()<=80?text.toUpperCase(Locale.ROOT):null;
 }
 @Transactional(readOnly=true) public List<Ticket> getRecentTickets(){return tickets.findTop10ByOrderByIdDesc();}
 @Transactional(readOnly=true) public List<Ticket> manifest(Long scheduleId,LocalDate date){if(scheduleId==null || date==null)return List.of();return tickets.findByBookingScheduleIdAndBookingTravelDate(scheduleId,date).stream().filter(t->t.getBooking().getStatus()==Booking.BookingStatus.CONFIRMED && t.getPayment()!=null && t.getPayment().getStatus()==Payment.PaymentStatus.COMPLETED).sorted(Comparator.comparing(Ticket::getTicketNumber)).toList();}
 @Transactional(isolation=Isolation.READ_COMMITTED) public void correctBoarding(Long id,String reason,String actor){
  reason=com.trainbooking.it25102925.service.ServiceStatusService.plainReason(reason);Ticket initial=tickets.findById(id).orElseThrow(()->new ResourceNotFoundException("Ticket not found."));
  var b=bookings.findByIdForUpdate(initial.getBooking().getId()).orElseThrow(()->new ResourceNotFoundException("Reservation not found."));refresh(b);
  var t=tickets.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Ticket not found."));refresh(t);t.setBooking(b);
  if(!Boolean.TRUE.equals(t.getIsBoarded()))throw new IllegalStateException("This ticket is already unboarded.");
  t.setIsBoarded(false);t.setBoardedAt(null);tickets.save(t);result(t,t.getTicketNumber(),BoardingLog.ScanResult.CORRECTED,reason,actor,b.getSchedule().getId(),b.getTravelDate());
 }
 @Transactional public void archiveLog(Long id,boolean archive,String actor){var row=logs.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Scan log not found."));refresh(row);row.setArchived(archive);row.setArchivedAt(LocalDateTime.now());row.setArchivedBy(actor);logs.save(row);}
 private void refresh(Object value){if(em!=null && value!=null)em.refresh(value);}
}
