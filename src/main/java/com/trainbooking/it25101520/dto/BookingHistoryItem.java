package com.trainbooking.it25101520.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** An immutable view of one reservation, without inventing a ticket for unpaid bookings. */
public record BookingHistoryItem(Long id, LocalDateTime bookedAt, String trainName, String origin,
        String destination, LocalDate travelDate, LocalTime departureTime, String seatNumbers,
        Integer seatCount, String seatClass, BigDecimal totalAmount, String status,
        String statusDetail, Long ticketId, String ticketNumber) {
    public String getReference() { return ticketNumber != null ? ticketNumber : "RES-" + id; }
    public String getClassLabel() { return "FIRST".equals(seatClass) ? "1st class" : "SECOND".equals(seatClass) ? "2nd class" : "Unavailable"; }
    public String getStatusLabel() { return switch (status) {
        case "PENDING" -> "Awaiting payment"; case "EXPIRED" -> "Expired";
        case "CONFIRMED" -> "Confirmed"; case "CANCELLED" -> "Cancelled";
        case "SERVICE_CANCELLED" -> "Service cancelled"; default -> "Unavailable";
    }; }
    public boolean isTicketAvailable() { return "CONFIRMED".equals(status) && ticketId != null; }
    public String getActionLabel() { return "PENDING".equals(status) ? "Continue payment" : isTicketAvailable() ? "View ticket" : "View reservation"; }
    public String getActionUrl() { return isTicketAvailable() ? "/payment/ticket/" + ticketId : "/booking/" + id + "/payment"; }
}
