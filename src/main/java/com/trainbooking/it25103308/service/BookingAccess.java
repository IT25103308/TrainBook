package com.trainbooking.it25103308.service;

import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25103308.model.Booking;
import java.security.Principal;

public final class BookingAccess {
    private BookingAccess() { }

    public static void requireOwner(Booking booking, Principal principal) {
        if (principal == null || booking == null || booking.getPassenger() == null
                || booking.getPassenger().getEmail() == null
                || !booking.getPassenger().getEmail().equalsIgnoreCase(principal.getName())) {
            throw new ResourceNotFoundException("Reservation not found.");
        }
    }
}
