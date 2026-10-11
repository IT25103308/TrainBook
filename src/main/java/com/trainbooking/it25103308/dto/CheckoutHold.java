package com.trainbooking.it25103308.dto;

import com.trainbooking.it25103308.model.Booking;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/** A server-calculated snapshot, refreshed on every checkout request. */
public record CheckoutHold(String state, long remainingMillis) {
    public static CheckoutHold from(Booking booking, LocalDateTime now) {
        if (booking.getStatus() == Booking.BookingStatus.CONFIRMED) {
            return new CheckoutHold("CONFIRMED", 0);
        }
        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            if (booking.getCancellationReason() == Booking.CancellationReason.HOLD_EXPIRED) {
                return new CheckoutHold("EXPIRED_RELEASED", 0);
            }
            if (booking.getCancellationReason() == Booking.CancellationReason.PASSENGER) {
                return new CheckoutHold("CANCELLED_BY_PASSENGER", 0);
            }
            return new CheckoutHold("CANCELLED", 0);
        }
        if (booking.getStatus() != Booking.BookingStatus.PENDING || booking.getLockExpiresAt() == null) {
            return new CheckoutHold("UNAVAILABLE", 0);
        }
        long remaining = Math.max(0, Duration.between(now, booking.getLockExpiresAt()).toMillis());
        if (remaining > 0 && !CheckoutRecovery.from(booking, now).canSelectAgain()) {
            boolean serviceCancelled = booking.getSchedule() != null && booking.getTravelDate()!=null
                    && com.trainbooking.it25102327.service.ServicePlan.resolve(booking.getSchedule(),booking.getTravelDate()).isCancelled();
            return new CheckoutHold(serviceCancelled ? "SERVICE_CANCELLED" : "UNAVAILABLE", 0);
        }
        return new CheckoutHold(remaining > 0 ? "PENDING" : "EXPIRED", remaining);
    }

    public boolean isPayable() {
        return "PENDING".equals(state) && remainingMillis > 0;
    }

    public String getDisplayTime() {
        if (!isPayable()) {
            return switch (state) {
                case "CONFIRMED" -> "PAID";
                case "EXPIRED_RELEASED" -> "EXPIRED";
                case "CANCELLED_BY_PASSENGER" -> "CANCELLED";
                case "SERVICE_CANCELLED" -> "SERVICE CANCELLED";
                default -> state;
            };
        }
        long seconds = (remainingMillis + 999) / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    public boolean isRecoverable() {
        return !isPayable() && !"CONFIRMED".equals(state);
    }

    public String getTitle() {
        return switch (state) {
            case "PENDING" -> "Temporary Seat Hold Active";
            case "EXPIRED", "EXPIRED_RELEASED" -> "Seat Hold Expired";
            case "CANCELLED_BY_PASSENGER" -> "Reservation Cancelled by You";
            case "CANCELLED" -> "Reservation Cancelled";
            case "SERVICE_CANCELLED" -> "Train Service Cancelled";
            case "CONFIRMED" -> "Payment Completed";
            default -> "Reservation Unavailable";
        };
    }

    public String getMessage() {
        return switch (state) {
            case "PENDING" -> "Complete payment before the remaining time expires. Refreshing does not extend your hold.";
            case "EXPIRED" -> "Your seat hold has expired. Payment is unavailable. Expired holds are released automatically; select seats again to check current availability.";
            case "EXPIRED_RELEASED" -> "Your seat hold expired and its seats were released. Select available seats again or search another train.";
            case "CANCELLED_BY_PASSENGER" -> "You cancelled this reservation and its held seats were released.";
            case "CANCELLED" -> "This reservation is cancelled and its seats are no longer held. Select available seats again or search another train.";
            case "SERVICE_CANCELLED" -> "This train service has been cancelled. Payment is unavailable. Please search for another train; your reservation record has been kept.";
            case "CONFIRMED" -> "This reservation is paid. View its ticket in My Bookings.";
            default -> "Payment is unavailable for this reservation. Please search another train.";
        };
    }
}
