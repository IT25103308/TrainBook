package com.trainbooking.it25101520.service;

import com.trainbooking.it25101520.dto.BookingHistoryItem;
import com.trainbooking.it25100977.model.Ticket;
import com.trainbooking.it25100977.repository.TicketRepository;
import com.trainbooking.it25103308.dto.CheckoutHold;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingHistoryService {
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;

    public static String normalizeFilter(String filter) {
        String value = filter == null ? "ALL" : filter.strip().toUpperCase(Locale.ROOT);
        return Set.of("ALL", "PENDING", "UPCOMING", "COMPLETED", "EXPIRED", "CANCELLED").contains(value) ? value : "ALL";
    }

    @Transactional(readOnly = true)
    public List<BookingHistoryItem> getBookings(Long passengerId, String filter) {
        LocalDateTime now = LocalDateTime.now();
        var bookings = bookingRepository.findByPassengerIdOrderByBookedAtDescIdDesc(passengerId);
        if (bookings.isEmpty()) return List.of();
        Map<Long, Ticket> tickets = ticketRepository.findByBookingIdIn(bookings.stream().map(Booking::getId).toList())
                .stream().collect(Collectors.toMap(t -> t.getBooking().getId(), Function.identity(), (first, second) -> first));
        String selected = normalizeFilter(filter);
        return bookings.stream().map(b -> snapshot(b, tickets.get(b.getId()), now))
                .filter(row -> matches(row, selected, now)).toList();
    }

    private BookingHistoryItem snapshot(Booking b, Ticket ticket, LocalDateTime now) {
        if (ticket != null && (ticket.getPayment() == null || ticket.getPayment().getStatus() != com.trainbooking.it25100977.model.Payment.PaymentStatus.COMPLETED)) ticket = null;
        CheckoutHold hold = CheckoutHold.from(b, now);
        String state = hold.state();
        String status = state.startsWith("EXPIRED") ? "EXPIRED" : state.startsWith("CANCELLED") ? "CANCELLED" : state;
        var schedule = b.getSchedule();
        var train = schedule == null ? null : schedule.getTrain();
        var route = schedule == null ? null : schedule.getRoute();
        String detail = hold.isPayable() ? "Hold ends at " + b.getLockExpiresAt().toLocalTime().withNano(0)
                : "CONFIRMED".equals(state) ? (ticket == null ? "Ticket unavailable. Please contact support." : "Payment completed")
                : "EXPIRED".equals(status) ? "Seat hold expired"
                : "CANCELLED_BY_PASSENGER".equals(state) ? "Cancelled by you"
                : "CANCELLED".equals(status) ? "Reservation cancelled" : "Payment unavailable";
        if ("SERVICE_CANCELLED".equals(status)) detail = "Train service cancelled; payment unavailable";
        return new BookingHistoryItem(b.getId(), b.getBookedAt(), train == null ? "Unavailable" : train.getTrainName(),
                route == null ? "Unavailable" : route.getOrigin(), route == null ? "Unavailable" : route.getDestination(),
                b.getTravelDate(), schedule == null ? null : schedule.getDepartureTime(), b.getSeatNumbers(),
                b.getNumberOfSeats(), b.getSeatClass() == null ? null : b.getSeatClass().name(), b.getTotalAmount(),
                status, detail, ticket == null ? null : ticket.getId(), ticket == null ? null : ticket.getTicketNumber());
    }

    private boolean matches(BookingHistoryItem row, String filter, LocalDateTime now) {
        boolean past = row.travelDate() != null && !row.travelDate()
                .atTime(row.departureTime() == null ? LocalTime.MAX : row.departureTime()).isAfter(now);
        return switch (filter) {
            case "UPCOMING" -> "CONFIRMED".equals(row.status()) && !past;
            case "COMPLETED" -> "CONFIRMED".equals(row.status()) && past;
            case "CANCELLED" -> "CANCELLED".equals(row.status()) || "SERVICE_CANCELLED".equals(row.status());
            case "PENDING", "EXPIRED" -> filter.equals(row.status());
            default -> true;
        };
    }
}
