package com.trainbooking.it25103308;

import com.trainbooking.it25103308.dto.CheckoutHold;
import com.trainbooking.it25103308.model.Booking;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutHoldTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 5, 10, 0);

    @Test
    void refreshUsesSameSavedDeadlineWithLessRemainingTime() {
        Booking booking = pending(now.plusMinutes(8).plusSeconds(3));
        CheckoutHold first = CheckoutHold.from(booking, now);
        CheckoutHold refreshed = CheckoutHold.from(booking, now.plusSeconds(30));
        assertEquals("08:03", first.getDisplayTime());
        assertEquals("07:33", refreshed.getDisplayTime());
        assertEquals(30000, first.remainingMillis() - refreshed.remainingMillis());
        assertTrue(refreshed.isPayable());
    }

    @Test
    void paymentUnavailableExactlyAtExpiryAndAfterwards() {
        Booking booking = pending(now);
        assertEquals("EXPIRED", CheckoutHold.from(booking, now).state());
        assertFalse(CheckoutHold.from(booking, now).isPayable());
        assertEquals(0, CheckoutHold.from(booking, now.plusMinutes(1)).remainingMillis());
    }

    @Test
    void missingDeadlineFailsClosed() {
        CheckoutHold hold = CheckoutHold.from(pending(null), now);
        assertEquals("UNAVAILABLE", hold.state());
        assertFalse(hold.isPayable());
    }

    @Test
    void paidBookingNeverRestartsTimer() {
        Booking booking = pending(now.plusMinutes(10));
        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        CheckoutHold hold = CheckoutHold.from(booking, now);
        assertEquals("PAID", hold.getDisplayTime());
        assertFalse(hold.isPayable());
        assertEquals(0, hold.remainingMillis());
    }

    @Test
    void cancelledBookingNeverEnablesPayment() {
        Booking booking = pending(now.plusMinutes(10));
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        assertEquals("CANCELLED", CheckoutHold.from(booking, now).getDisplayTime());
        assertFalse(CheckoutHold.from(booking, now).isPayable());
    }

    @Test
    void automaticExpiryRemainsLabelledExpiredAfterCleanup() {
        Booking booking = pending(now.minusSeconds(1));
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        booking.setCancellationReason(Booking.CancellationReason.HOLD_EXPIRED);
        CheckoutHold hold = CheckoutHold.from(booking, now);
        assertEquals("EXPIRED_RELEASED", hold.state());
        assertEquals("EXPIRED", hold.getDisplayTime());
        assertEquals("Seat Hold Expired", hold.getTitle());
        assertTrue(hold.getMessage().contains("seats were released"));
        assertTrue(hold.isRecoverable());
        assertFalse(hold.isPayable());
    }

    @Test
    void recordedPassengerCancellationUsesAccurateWording() {
        Booking booking = pending(now.plusMinutes(3));
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        booking.setCancellationReason(Booking.CancellationReason.PASSENGER);
        CheckoutHold hold = CheckoutHold.from(booking, now);
        assertEquals("CANCELLED_BY_PASSENGER", hold.state());
        assertEquals("Reservation Cancelled by You", hold.getTitle());
        assertTrue(hold.getMessage().startsWith("You cancelled"));
        assertFalse(hold.isPayable());
    }

    @Test
    void legacyCancellationDoesNotInventACause() {
        Booking booking = pending(now.minusMinutes(10));
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        CheckoutHold hold = CheckoutHold.from(booking, now);
        assertEquals("CANCELLED", hold.state());
        assertEquals("Reservation Cancelled", hold.getTitle());
        assertFalse(hold.getMessage().contains("You cancelled"));
        assertFalse(hold.getMessage().contains("expired"));
    }

    private Booking pending(LocalDateTime expiry) {
        var date = now.toLocalDate().plusDays(1);
        return Booking.builder().status(Booking.BookingStatus.PENDING).lockExpiresAt(expiry).travelDate(date)
                .schedule(com.trainbooking.it25102327.model.Schedule.builder().id(1L).isActive(true)
                        .dayOfWeek(date.getDayOfWeek()).departureTime(java.time.LocalTime.of(8, 30))
                        .train(com.trainbooking.it25102327.model.Train.builder()
                                .status(com.trainbooking.it25102327.model.Train.TrainStatus.ON_TIME).build()).build()).build();
    }
}
