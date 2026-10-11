package com.trainbooking.it25100228.service;
import com.trainbooking.it25100228.dto.DashboardStatsDto;
import com.trainbooking.it25100228.model.BoardingLog;
import com.trainbooking.it25100228.repository.BoardingLogRepository;
import com.trainbooking.it25100977.model.*;
import com.trainbooking.it25100977.repository.*;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.repository.TrainRepository;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class DashboardService {
 private final BookingRepository bookings;private final PaymentRepository payments;private final TrainRepository trains;private final UserRepository users;private final BoardingLogRepository logs;private final TicketRepository tickets;
 public DashboardStatsDto getDashboardStats(){var all=bookings.findAll();var completed=payments.findAll().stream().filter(p->p.getStatus()==Payment.PaymentStatus.COMPLETED).toList();LocalDate today=LocalDate.now();LocalDateTime now=LocalDateTime.now();var boarded=tickets.findAll().stream().filter(t->Boolean.TRUE.equals(t.getIsBoarded()) && t.getBooking()!=null && t.getBooking().getStatus()==Booking.BookingStatus.CONFIRMED && t.getPayment()!=null && t.getPayment().getStatus()==Payment.PaymentStatus.COMPLETED).toList();
  return DashboardStatsDto.builder().totalBookings((long)all.size()).totalRevenue(sum(completed)).todayRevenue(sum(completed.stream().filter(p->p.getPaidAt()!=null && today.equals(p.getPaidAt().toLocalDate())).toList()))
   .activeTrains(trains.findAll().stream().filter(t->t.getStatus()!=null && Set.of("ACTIVE","ON_TIME","DELAYED").contains(t.getStatus().name())).count())
   .pendingBookings(all.stream().filter(b->b.getStatus()==Booking.BookingStatus.PENDING && b.getLockExpiresAt()!=null && b.getLockExpiresAt().isAfter(now)).count())
   .expiredPendingBookings(all.stream().filter(b->b.getStatus()==Booking.BookingStatus.PENDING && (b.getLockExpiresAt()==null || !b.getLockExpiresAt().isAfter(now))).count())
   .todayBookings(all.stream().filter(b->b.getBookedAt()!=null && today.equals(b.getBookedAt().toLocalDate())).count())
   .totalPassengers(users.findAll().stream().filter(u->u.getRole()==User.Role.PASSENGER).count()).boardedTickets((long)boarded.size()).boardedPassengers(boarded.stream().map(Ticket::getBooking).map(Booking::getNumberOfSeats).filter(Objects::nonNull).mapToLong(Integer::longValue).sum()).scanAttempts(logs.count()).build();
 }
 public List<Booking> recentBookings(){return bookings.findAll().stream().sorted(Comparator.comparing(Booking::getId).reversed()).limit(20).toList();}
 public Map<String,Object> getRevenueByDate(LocalDate from,LocalDate to){checkRange(from,to);Map<String,BigDecimal> daily=new LinkedHashMap<>();from.datesUntil(to.plusDays(1)).forEach(d->daily.put(d.toString(),BigDecimal.ZERO));for(var p:payments.findAll())if(p.getStatus()==Payment.PaymentStatus.COMPLETED && p.getPaidAt()!=null && p.getAmount()!=null){String key=p.getPaidAt().toLocalDate().toString();if(daily.containsKey(key))daily.put(key,daily.get(key).add(p.getAmount()));}return Map.of("dailyRevenue",daily,"from",from.toString(),"to",to.toString());}
 public List<BoardingLog> getRecentBoardingLogs(){return getRecentBoardingLogs(false);}
 public List<BoardingLog> getRecentBoardingLogs(boolean archived){return logs.findTop100ByOrderByIdDesc().stream().filter(l->Boolean.TRUE.equals(l.getArchived())==archived).toList();}
 public Map<String,Object> getReconciliationReport(){return getReconciliationReport(LocalDate.now(),LocalDate.now());}
 public Map<String,Object> getReconciliationReport(LocalDate from,LocalDate to){checkRange(from,to);var selected=payments.findAll().stream().filter(p->{var time=p.getStatus()==Payment.PaymentStatus.COMPLETED?p.getPaidAt():p.getAttemptedAt();return time!=null && !time.toLocalDate().isBefore(from) && !time.toLocalDate().isAfter(to);}).toList();Map<String,Object> r=new LinkedHashMap<>();r.put("from",from);r.put("to",to);r.put("totalTransactions",selected.size());r.put("successfulTransactions",selected.stream().filter(p->p.getStatus()==Payment.PaymentStatus.COMPLETED).count());r.put("declinedTransactions",selected.stream().filter(p->p.getStatus()==Payment.PaymentStatus.FAILED).count());r.put("cancelledIntents",selected.stream().filter(p->p.getStatus()==Payment.PaymentStatus.CANCELLED).count());r.put("grossSettlementAmount",sum(selected.stream().filter(p->p.getStatus()==Payment.PaymentStatus.COMPLETED).toList()));r.put("gatewayStatus","DEMO SIMULATION — no external settlement");r.put("reconciledAt",LocalDateTime.now());r.put("undatedPaymentRecords",payments.findAll().stream().filter(p->p.getStatus()==Payment.PaymentStatus.COMPLETED && p.getPaidAt()==null).count());return r;}
 private BigDecimal sum(List<Payment> rows){return rows.stream().map(Payment::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
 public static void checkRange(LocalDate from,LocalDate to){if(from==null || to==null || from.isAfter(to) || java.time.temporal.ChronoUnit.DAYS.between(from,to)>365)throw new IllegalArgumentException("Choose an ordered date range of at most 366 days.");}
}
