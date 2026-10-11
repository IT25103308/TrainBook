package com.trainbooking.it25100977;

import com.trainbooking.it25100977.dto.PaymentRequest;
import com.trainbooking.it25100977.model.Payment;
import com.trainbooking.it25100977.repository.PaymentRepository;
import com.trainbooking.it25100977.repository.TicketRepository;
import com.trainbooking.it25100977.service.PaymentService;
import com.trainbooking.it25102925.service.EmailService;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentExpiryTest {
    private BookingRepository bookings;
    private PaymentRepository payments;
    private TicketRepository tickets;
    private PaymentService service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingRepository.class);
        payments = mock(PaymentRepository.class);
        tickets = mock(TicketRepository.class);
        var schedules=mock(com.trainbooking.it25102327.repository.ScheduleRepository.class);
        service = new PaymentService(payments, tickets, bookings, mock(EmailService.class),schedules, null);
        booking = Booking.builder().id(12L).status(Booking.BookingStatus.PENDING)
                .lockExpiresAt(LocalDateTime.now().plusMinutes(5)).totalAmount(new BigDecimal("3000.00")).build();
        booking.setTravelDate(java.time.LocalDate.now().plusDays(1));
        booking.setSchedule(com.trainbooking.it25102327.model.Schedule.builder().id(1L).isActive(true)
                .dayOfWeek(booking.getTravelDate().getDayOfWeek()).departureTime(java.time.LocalTime.of(8, 30))
                .train(com.trainbooking.it25102327.model.Train.builder().id(1L)
                        .status(com.trainbooking.it25102327.model.Train.TrainStatus.ON_TIME).build()).build());
        when(schedules.findByIdForUpdate(1L)).thenAnswer(call->Optional.of(booking.getSchedule()));
        when(bookings.findByIdForUpdate(12L)).thenReturn(Optional.of(booking));
    }

    @Test
    void expiredHoldIsRejectedBeforePaymentOrConfirmation() {
        booking.setLockExpiresAt(LocalDateTime.now().minusSeconds(1));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.processPayment(12L, new PaymentRequest()));
        assertTrue(error.getMessage().contains("expired"));
        assertEquals(Booking.BookingStatus.PENDING, booking.getStatus());
        verifyNoInteractions(payments, tickets);
        verify(bookings, never()).save(any());
    }

    @Test
    void missingDeadlineCannotBeUsedToBypassExpiry() {
        booking.setLockExpiresAt(null);
        assertThrows(IllegalStateException.class, () -> service.processPayment(12L, new PaymentRequest()));
        verifyNoInteractions(payments, tickets);
    }

    @Test
    void cancelledHoldCannotBeResurrectedByPayment() {
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () -> service.processPayment(12L, new PaymentRequest()));
        assertEquals(Booking.BookingStatus.CANCELLED, booking.getStatus());
        verifyNoInteractions(payments, tickets);
    }

    @Test
    void repeatedSubmitReturnsCompletedPaymentWithoutAnotherCharge() {
        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        booking.setLockExpiresAt(LocalDateTime.now().minusDays(1));
        Payment completed = Payment.builder().id(20L).booking(booking).status(Payment.PaymentStatus.COMPLETED).build();
        when(payments.findByBookingId(12L)).thenReturn(Optional.of(completed));
        assertSame(completed, service.processPayment(12L, new PaymentRequest()));
        verify(payments, never()).save(any());
        verify(bookings, never()).save(any());
    }

    @Test
    void confirmedBookingWithoutCompletedPaymentRequiresReview() {
        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        when(payments.findByBookingId(12L)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.processPayment(12L, new PaymentRequest()));
        verify(payments, never()).save(any());
    }

    @Test
    void ticketRequestCannotAutomaticallyPayForPendingReservation() {
        assertThrows(IllegalStateException.class, () -> service.generateTicket(12L));
        verifyNoInteractions(payments, tickets);
        verify(bookings, never()).save(any());
    }

    @Test
    void ticketNeedsCompletedPaymentEvenWhenBookingSaysConfirmed() {
        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        when(payments.findByBookingId(12L)).thenReturn(Optional.of(
                Payment.builder().status(Payment.PaymentStatus.FAILED).build()));
        assertThrows(IllegalStateException.class, () -> service.generateTicket(12L));
        verify(payments, never()).save(any());
        verifyNoInteractions(tickets);
    }

    @Test void cancellationWhileAHoldIsActiveBlocksPaymentBeforeAnyWrites() {
        booking.getSchedule().getTrain().setStatus(com.trainbooking.it25102327.model.Train.TrainStatus.CANCELLED);
        assertThrows(IllegalStateException.class,()->service.processPayment(12L,new PaymentRequest()));
        assertEquals(Booking.BookingStatus.PENDING,booking.getStatus()); verifyNoInteractions(payments,tickets);
        verify(bookings,never()).save(any());
        assertEquals("SERVICE_CANCELLED",com.trainbooking.it25103308.dto.CheckoutHold.from(booking,LocalDateTime.now()).state());
    }
    @Test void inactiveMaintenanceOrMissingJourneyCannotTakePayment() {
        booking.getSchedule().setIsMaintenanceBlocked(true);
        assertThrows(IllegalStateException.class,()->service.processPayment(12L,new PaymentRequest()));
        booking.setSchedule(null);
        assertThrows(IllegalStateException.class,()->service.processPayment(12L,new PaymentRequest()));
        verifyNoInteractions(payments,tickets);
    }
    @Test void delayDoesNotExtendTheScheduledDepartureCutoff() {
        booking.setTravelDate(java.time.LocalDate.now());
        booking.getSchedule().setDayOfWeek(booking.getTravelDate().getDayOfWeek());
        booking.getSchedule().setDepartureTime(java.time.LocalTime.MIDNIGHT);
        booking.getSchedule().getTrain().setStatus(com.trainbooking.it25102327.model.Train.TrainStatus.DELAYED);
        assertThrows(IllegalStateException.class,()->service.processPayment(12L,new PaymentRequest()));
        verifyNoInteractions(payments,tickets);
    }

}
