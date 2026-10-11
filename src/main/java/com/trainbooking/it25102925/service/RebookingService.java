package com.trainbooking.it25102925.service;
import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25100977.model.Payment;
import com.trainbooking.it25100977.repository.*;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25102327.service.ServicePlan;
import com.trainbooking.it25102925.dto.RebookingOption;
import com.trainbooking.it25102925.model.*;
import com.trainbooking.it25102925.repository.*;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25103308.service.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;

@Service @RequiredArgsConstructor
public class RebookingService {
    private final RebookingTokenRepository tokens;
    private final BookingRepository bookings;
    private final ScheduleRepository schedules;
    private final PaymentRepository payments;
    private final TicketRepository tickets;
    private final BookingService inventory;
    private final RebookingTransferRepository transfers;
    private final EntityManager entityManager;

    @Transactional(readOnly=true)
    public RebookingToken offer(String value,String email) {
        var token=readToken(value);BookingAccess.requireOwner(token.getBooking(),email==null?null:()->email);
        eligible(token,token.getBooking(),LocalDateTime.now());return token;
    }
    @Transactional(readOnly=true)
    public List<RebookingOption> alternatives(String value,String email) {
        var token=offer(value,email);var booking=token.getBooking();LocalDateTime original=originalDeparture(booking);
        var result=new ArrayList<RebookingOption>();
        for(Schedule schedule:schedules.findAll()) for(LocalDate date:List.of(original.toLocalDate(),original.toLocalDate().plusDays(1))) {
            var plan=ServicePlan.resolve(schedule,date);
            if(!compatible(booking,schedule,date,plan,LocalDateTime.now(),original)) continue;
            var seats=inventory.getSeatAvailability(schedule.getId(),date,booking.getSeatClass().name());
            if(seats.warning()!=null || seats.availableSeats()<booking.getNumberOfSeats()) continue;
            result.add(new RebookingOption(schedule.getId(),date,schedule.getTrain().getTrainName(),schedule.getTrain().getTrainNumber(),schedule.getRoute().getOrigin(),schedule.getRoute().getDestination(),
                plan.departure(),plan.arrival(),plan.arrivalDayOffset(),seats.availableSeats(),booking.getSeatClass().name()));
        }
        return result.stream().sorted(Comparator.comparing((RebookingOption o)->o.date().atTime(o.departure())).thenComparing(RebookingOption::scheduleId)).limit(10).toList();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Booking transfer(String value,Long targetId,LocalDate date,String email) {
        var initial=readToken(value);Long bookingId=initial.getBooking().getId();
        Booking booking=bookings.findByIdForUpdate(bookingId).orElseThrow(()->missing());refresh(booking);
        BookingAccess.requireOwner(booking,email==null?null:()->email);
        var token=tokens.findByIdForUpdate(initial.getId()).orElseThrow(()->missing());refresh(token);
        if(targetId==null || targetId<1 || date==null) throw new IllegalArgumentException("Choose an available replacement departure.");
        Long sourceId=booking.getSchedule().getId();Map<Long,Schedule> locked=new HashMap<>();
        for(Long id:new TreeSet<>(List.of(sourceId,targetId))) {
            var schedule=schedules.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Replacement service not found."));
            refresh(schedule);if(entityManager!=null){entityManager.refresh(schedule.getTrain());entityManager.refresh(schedule.getRoute());}locked.put(id,schedule);
        }
        booking.setSchedule(locked.get(sourceId));LocalDateTime now=LocalDateTime.now();eligible(token,booking,now);
        var target=locked.get(targetId);var plan=ServicePlan.resolve(target,date);LocalDateTime original=originalDeparture(booking);
        if(!compatible(booking,target,date,plan,now,original)) throw new IllegalArgumentException("Choose an available service on the same route and class, departing after your original time and within 24 hours.");
        var seats=inventory.getSeatAvailability(targetId,date,booking.getSeatClass().name());
        if(seats.warning()!=null || seats.availableSeats()<booking.getNumberOfSeats()) throw new IllegalStateException("The replacement no longer has enough seats. Choose another departure.");
        Set<String> occupied=new HashSet<>(seats.occupiedSeats());
        var selected=IntStream.rangeClosed(1,seats.capacity()).mapToObj(n->SeatSelection.label(booking.getSeatClass(),n)).filter(s->!occupied.contains(s)).limit(booking.getNumberOfSeats()).toList();
        if(selected.size()!=booking.getNumberOfSeats()) throw new IllegalStateException("The replacement seats need staff review.");
        var history=RebookingTransfer.builder().bookingId(bookingId).tokenId(token.getId()).fromScheduleId(sourceId).toScheduleId(targetId).fromDate(booking.getTravelDate()).toDate(date)
            .fromTrain(booking.getTrainName()).toTrain(target.getTrain().getTrainName()).origin(booking.getOrigin()).destination(booking.getDestination()).fromSeats(booking.getSeatNumbers())
            .toSeats(String.join(", ",selected)).paidAmount(booking.getTotalAmount()).transferredAt(now).build();
        booking.setSchedule(target);booking.setTravelDate(date);booking.setSeatNumbers(history.getToSeats());booking.setDepartureTimeSnapshot(plan.departure());booking.setArrivalTimeSnapshot(plan.arrival());booking.setArrivalDayOffsetSnapshot(plan.arrivalDayOffset());
        bookings.save(booking);token.setIsRedeemed(true);tokens.save(token);
        for(var other:tokens.findByBookingIdAndIsRedeemedFalse(bookingId)){other.setIsRedeemed(true);tokens.save(other);}
        transfers.save(history);return booking;
    }
    private void eligible(RebookingToken token,Booking booking,LocalDateTime now) {
        if(Boolean.TRUE.equals(token.getIsRedeemed()) || token.getExpiresAt()==null || !token.getExpiresAt().isAfter(now)) throw new IllegalStateException("This replacement offer expired or was already used.");
        if(booking.getSchedule()==null || booking.getTravelDate()==null || booking.getSeatClass()==null || booking.getNumberOfSeats()==null || booking.getNumberOfSeats()<1 || booking.getNumberOfSeats()>6
            || token.getSourceScheduleId()==null || !Objects.equals(token.getSourceScheduleId(),booking.getSchedule().getId()) || !Objects.equals(token.getSourceDate(),booking.getTravelDate())
            || !Objects.equals(token.getSourceClass(),booking.getSeatClass().name())) throw new IllegalStateException("This offer is outdated. Open your latest cancellation notification.");
        if(booking.getStatus()!=Booking.BookingStatus.CONFIRMED || !ServicePlan.resolve(booking.getSchedule(),booking.getTravelDate()).isCancelled()) throw new IllegalStateException("Only an eligible paid reservation on a currently cancelled departure can use this offer.");
        var payment=payments.findByBookingId(booking.getId()).filter(p->p.getStatus()==Payment.PaymentStatus.COMPLETED).orElseThrow(()->new IllegalStateException("No completed payment exists for this reservation."));
        var ticket=tickets.findByBookingId(booking.getId()).orElseThrow(()->new IllegalStateException("The paid ticket needs staff review."));
        if(Boolean.TRUE.equals(ticket.getIsBoarded()) || ticket.getPayment()==null || !Objects.equals(ticket.getPayment().getId(),payment.getId()) || payment.getAmount()==null
            || booking.getTotalAmount()==null || payment.getAmount().compareTo(booking.getTotalAmount())!=0) throw new IllegalStateException("A boarded or inconsistent paid ticket cannot be transferred.");
        if(!originalDeparture(booking).plusHours(24).isAfter(now)) throw new IllegalStateException("No eligible replacement remains in this departure's 24-hour window.");
    }
    private boolean compatible(Booking booking,Schedule target,LocalDate date,ServicePlan plan,LocalDateTime now,LocalDateTime original) {
        if(target.getTrain()==null || target.getRoute()==null || plan.arrival()==null || !plan.availableAt(now) || !sameStation(target.getRoute().getOrigin(),booking.getOrigin()) || !sameStation(target.getRoute().getDestination(),booking.getDestination())) return false;
        if(Objects.equals(target.getId(),booking.getSchedule().getId()) && Objects.equals(date,booking.getTravelDate())) return false;
        LocalDateTime departure=date.atTime(plan.departure());return !departure.isBefore(original) && !departure.isAfter(original.plusHours(24));
    }
    private LocalDateTime originalDeparture(Booking booking) {
        LocalTime time=booking.getDepartureTimeSnapshot()!=null?booking.getDepartureTimeSnapshot():ServicePlan.resolve(booking.getSchedule(),booking.getTravelDate()).departure();
        if(time==null) throw new IllegalStateException("The original departure needs staff review.");return booking.getTravelDate().atTime(time);
    }
    private RebookingToken readToken(String value) {
        if(value==null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw missing();
        return tokens.findByToken(value.toLowerCase(Locale.ROOT)).orElseThrow(()->missing());
    }
    private ResourceNotFoundException missing(){return new ResourceNotFoundException("Replacement offer not found.");}
    private boolean sameStation(String a,String b){return a!=null && b!=null && a.strip().equalsIgnoreCase(b.strip());}
    private void refresh(Object value){if(entityManager!=null)entityManager.refresh(value);}
}
