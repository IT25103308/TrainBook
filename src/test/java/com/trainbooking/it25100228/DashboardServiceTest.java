package com.trainbooking.it25100228;
import com.trainbooking.it25100228.service.DashboardService;
import com.trainbooking.it25100228.repository.BoardingLogRepository;
import com.trainbooking.it25100977.model.*;
import com.trainbooking.it25100977.repository.*;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.repository.TrainRepository;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25103308.model.Booking;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DashboardServiceTest {
 PaymentRepository payments=mock(PaymentRepository.class);TicketRepository tickets=mock(TicketRepository.class);BookingRepository bookings=mock(BookingRepository.class);BoardingLogRepository logs=mock(BoardingLogRepository.class);
 DashboardService service=new DashboardService(bookings,payments,mock(TrainRepository.class),mock(UserRepository.class),logs,tickets);
 Payment payment(String amount,LocalDate day){return Payment.builder().amount(new BigDecimal(amount)).status(Payment.PaymentStatus.COMPLETED).paidAt(day==null?null:day.atTime(12,0)).build();}
 @Test void zeroRevenueTodayIsNotReplacedByLifetimeRevenueAndDecimalsArePreserved(){when(payments.findAll()).thenReturn(List.of(payment("123.45",LocalDate.now().minusDays(1)),payment("10.50",null)));var stats=service.getDashboardStats();assertEquals(new BigDecimal("133.95"),stats.getTotalRevenue());assertEquals(BigDecimal.ZERO,stats.getTodayRevenue());}
 @Test void periodIncludesZeroDaysUsesPaidDateAndExcludesUndatedAndFailedRecords(){LocalDate day=LocalDate.now();when(payments.findAll()).thenReturn(List.of(payment("123.45",day.minusDays(1)),payment("10.50",null),Payment.builder().amount(new BigDecimal("999.00")).status(Payment.PaymentStatus.FAILED).attemptedAt(day.atTime(12,0)).build()));var report=service.getReconciliationReport(day,day);assertEquals(BigDecimal.ZERO,report.get("grossSettlementAmount"));assertEquals(1L,report.get("declinedTransactions"));assertEquals(1L,report.get("undatedPaymentRecords"));var rows=(Map<?,?>)service.getRevenueByDate(day.minusDays(1),day).get("dailyRevenue");assertEquals(new BigDecimal("123.45"),rows.get(day.minusDays(1).toString()));assertEquals(BigDecimal.ZERO,rows.get(day.toString()));assertThrows(IllegalArgumentException.class,()->service.getRevenueByDate(day,day.minusDays(1)));assertThrows(IllegalArgumentException.class,()->service.getRevenueByDate(day.minusYears(2),day));}
 @Test void boardedSeatsTicketsAndAttemptsAreDifferentUnits(){var b=Booking.builder().status(Booking.BookingStatus.CONFIRMED).numberOfSeats(4).build();var paid=Payment.builder().status(Payment.PaymentStatus.COMPLETED).build();when(tickets.findAll()).thenReturn(List.of(Ticket.builder().isBoarded(true).booking(b).payment(paid).build(),Ticket.builder().isBoarded(false).booking(b).payment(paid).build()));when(logs.count()).thenReturn(5L);var stats=service.getDashboardStats();assertEquals(4L,stats.getBoardedPassengers());assertEquals(1L,stats.getBoardedTickets());assertEquals(5L,stats.getScanAttempts());}
}
