package com.trainbooking.it25103308;

import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.model.Train;
import com.trainbooking.it25103308.dto.CheckoutRecovery;
import com.trainbooking.it25103308.model.Booking;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutRecoveryTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 5, 10, 0);

    @Test
    void upcomingJourneyUsesSameScheduleAndDateWithoutOldSeatSelections() {
        CheckoutRecovery recovery = CheckoutRecovery.from(booking(), now);
        assertTrue(recovery.canSelectAgain());
        assertEquals(3600000, recovery.departureRemainingMillis());
        assertEquals("/booking/seats?scheduleId=32&date=2026-10-05", recovery.selectionUrl());
        assertFalse(recovery.selectionUrl().contains("seatNumbers"));
    }

    @Test
    void departureBecomingPastMakesTheSameLinkUnavailable() {
        Booking booking = booking();
        assertTrue(CheckoutRecovery.from(booking, now).canSelectAgain());
        CheckoutRecovery after = CheckoutRecovery.from(booking, now.plusHours(1));
        assertFalse(after.canSelectAgain());
        assertNull(after.selectionUrl());
        assertTrue(after.message().contains("already passed"));
    }

    @Test
    void previousTravelDateCannotOfferSeatSelection() {
        Booking booking = booking();
        booking.setTravelDate(now.toLocalDate().minusDays(7));
        assertFalse(CheckoutRecovery.from(booking, now).canSelectAgain());
    }

    @Test
    void wrongWeekdayIsUnavailableEvenWhenDateIsFuture() {
        Booking booking = booking();
        booking.setTravelDate(now.toLocalDate().plusDays(1));
        assertFalse(CheckoutRecovery.from(booking, now).canSelectAgain());
    }

    @Test
    void inactiveAndMaintenanceBlockedSchedulesCannotOfferSeatSelection() {
        Booking booking = booking();
        booking.getSchedule().setIsActive(false);
        assertFalse(CheckoutRecovery.from(booking, now).canSelectAgain());
        booking.getSchedule().setIsActive(true);
        booking.getSchedule().setIsMaintenanceBlocked(true);
        assertFalse(CheckoutRecovery.from(booking, now).canSelectAgain());
    }

    @ParameterizedTest
    @EnumSource(value = Train.TrainStatus.class, names = {"CANCELLED", "INACTIVE", "MAINTENANCE"})
    void unavailableTrainCannotOfferSeatSelection(Train.TrainStatus status) {
        Booking booking = booking();
        booking.getSchedule().getTrain().setStatus(status);
        assertFalse(CheckoutRecovery.from(booking, now).canSelectAgain());
    }

    @ParameterizedTest
    @EnumSource(value = Train.TrainStatus.class, names = {"ON_TIME", "DELAYED", "ACTIVE"})
    void runningTrainWithUpcomingScheduledDepartureCanOfferSeatSelection(Train.TrainStatus status) {
        Booking booking = booking();
        booking.getSchedule().getTrain().setStatus(status);
        assertTrue(CheckoutRecovery.from(booking, now).canSelectAgain());
    }

    @Test
    void missingJourneyDataFailsClosed() {
        Booking booking = booking();
        booking.setTravelDate(null);
        assertFalse(CheckoutRecovery.from(booking, now).canSelectAgain());
        booking = booking();
        booking.getSchedule().setDepartureTime(null);
        assertFalse(CheckoutRecovery.from(booking, now).canSelectAgain());
        booking = booking();
        booking.getSchedule().setTrain(null);
        assertFalse(CheckoutRecovery.from(booking, now).canSelectAgain());
        booking.setSchedule(null);
        assertFalse(CheckoutRecovery.from(booking, now).canSelectAgain());
    }

    @Test
    void confirmedReservationOffersBookingsInsteadOfAnotherReservation() {
        Booking booking = booking();
        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        assertFalse(CheckoutRecovery.from(booking, now).canSelectAgain());
        assertTrue(CheckoutRecovery.from(booking, now).message().contains("already paid"));
    }

    private Booking booking() {
        Schedule schedule = Schedule.builder().id(32L).dayOfWeek(now.getDayOfWeek())
                .departureTime(now.plusHours(1).toLocalTime()).arrivalTime(now.plusHours(3).toLocalTime())
                .train(Train.builder().status(Train.TrainStatus.ACTIVE).build()).build();
        return Booking.builder().id(12L).schedule(schedule).travelDate(now.toLocalDate())
                .status(Booking.BookingStatus.CANCELLED).seatNumbers("Car 01 / S03").build();
    }
}
