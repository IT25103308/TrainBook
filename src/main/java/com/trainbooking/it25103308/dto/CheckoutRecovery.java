package com.trainbooking.it25103308.dto;

import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.model.Train;
import com.trainbooking.it25103308.model.Booking;
import java.time.Duration;
import java.time.LocalDateTime;

/** Read-only journey eligibility. Actual seat availability is fetched again on the seat map. */
public record CheckoutRecovery(boolean canSelectAgain, long departureRemainingMillis,
                               String selectionUrl, String message) {
    public static CheckoutRecovery from(Booking booking, LocalDateTime now) {
        if (booking.getStatus() == Booking.BookingStatus.CONFIRMED) {
            return unavailable("This reservation is already paid. View its ticket in My Bookings.");
        }
        Schedule schedule = booking.getSchedule();
        if (schedule == null || schedule.getId() == null || schedule.getId() < 1
                || booking.getTravelDate() == null || schedule.getDepartureTime() == null) {
            return unavailable("This journey is unavailable. Please search another train.");
        }
        var plan=com.trainbooking.it25102327.service.ServicePlan.resolve(schedule,booking.getTravelDate());
        if(plan.isCancelled()) return unavailable("This departure has been cancelled. Please search another service.");
        if(!plan.availableAt(now)) return unavailable("This service is unavailable on your travel date or its departure has already passed. Please search another train.");
        LocalDateTime departure=booking.getTravelDate().atTime(plan.departure());
        return new CheckoutRecovery(true, Duration.between(now, departure).toMillis(),
                "/booking/seats?scheduleId=" + schedule.getId() + "&date=" + booking.getTravelDate(),
                "Seat availability will be checked again. Choose from the seats available now.");
    }

    private static CheckoutRecovery unavailable(String message) {
        return new CheckoutRecovery(false, 0, null, message);
    }
}
