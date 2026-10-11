package com.trainbooking.it25103308.dto;

import java.util.List;

/** One class's seat map and quantity, read from the same set of bookings. */
public record SeatAvailability(int capacity, int availableSeats, List<String> occupiedSeats, String warning) {
}
