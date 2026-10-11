package com.trainbooking.it25101520;

import com.trainbooking.it25101520.service.BookingHistoryService;
import com.trainbooking.it25101520.controller.PassengerController;
import com.trainbooking.it25101520.service.PassengerService;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25100977.model.Ticket;
import com.trainbooking.it25100977.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingHistoryTest {
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final BookingHistoryService service = new BookingHistoryService(bookings, tickets);
    private Booking booking(long id, Booking.BookingStatus status, LocalDate date) {
        return Booking.builder().id(id).status(status).travelDate(date).numberOfSeats(1)
                .bookedAt(LocalDateTime.now().minusMinutes(id)).schedule(Schedule.builder().id(1L).isActive(true).dayOfWeek(date.getDayOfWeek()).departureTime(LocalTime.NOON)
                        .train(com.trainbooking.it25102327.model.Train.builder().status(com.trainbooking.it25102327.model.Train.TrainStatus.ON_TIME).build()).build())
                .lockExpiresAt(LocalDateTime.now().plusMinutes(5)).build();
    }
    private List<Booking> fixture() {
        var today = LocalDate.now();
        var active = booking(24L, Booking.BookingStatus.PENDING, today.plusDays(1));
        var expired = booking(23L, Booking.BookingStatus.PENDING, today.plusDays(1)); expired.setLockExpiresAt(LocalDateTime.now().minusSeconds(1));
        var cleanup = booking(22L, Booking.BookingStatus.CANCELLED, today.plusDays(1)); cleanup.setCancellationReason(Booking.CancellationReason.HOLD_EXPIRED);
        var cancelled = booking(21L, Booking.BookingStatus.CANCELLED, today.plusDays(1)); cancelled.setCancellationReason(Booking.CancellationReason.PASSENGER);
        var future = booking(20L, Booking.BookingStatus.CONFIRMED, today.plusDays(1));
        var past = booking(19L, Booking.BookingStatus.CONFIRMED, today.minusDays(1));
        var all = List.of(active, expired, cleanup, cancelled, future, past);
        when(bookings.findByPassengerIdOrderByBookedAtDescIdDesc(7L)).thenReturn(all);
        when(tickets.findByBookingIdIn(anyList())).thenReturn(List.of(Ticket.builder().id(10L).booking(future).payment(com.trainbooking.it25100977.model.Payment.builder().status(com.trainbooking.it25100977.model.Payment.PaymentStatus.COMPLETED).build()).ticketNumber("ISSUED-20").build()));
        return all;
    }
    @Test void usesNewestFirstQueryAndActualTicketIds() {
        fixture(); var rows = service.getBookings(7L, "ALL");
        assertEquals(List.of(24L,23L,22L,21L,20L,19L), rows.stream().map(r -> r.id()).toList());
        assertEquals("RES-24", rows.get(0).getReference()); assertEquals("Continue payment", rows.get(0).getActionLabel());
        assertEquals("/booking/24/payment", rows.get(0).getActionUrl()); assertFalse(rows.get(0).isTicketAvailable());
        assertEquals(10L, rows.get(4).ticketId()); assertEquals("ISSUED-20", rows.get(4).getReference());
        assertEquals("/payment/ticket/10", rows.get(4).getActionUrl());
        verify(bookings).findByPassengerIdOrderByBookedAtDescIdDesc(7L);
        verify(tickets).findByBookingIdIn(List.of(24L,23L,22L,21L,20L,19L));
    }
    @Test void expiryIsShownBeforeAndAfterCleanup() { fixture(); assertEquals(List.of(23L,22L), service.getBookings(7L,"EXPIRED").stream().map(r->r.id()).toList()); }
    @Test void cancellationDoesNotIncludeAutomaticExpiry() { fixture(); assertEquals(List.of(21L), service.getBookings(7L,"CANCELLED").stream().map(r->r.id()).toList()); }
    @Test void pendingFilterOnlyContainsPayableHolds() { fixture(); assertEquals(List.of(24L), service.getBookings(7L,"PENDING").stream().map(r->r.id()).toList()); }
    @Test void tripFiltersExcludeUnpaidAndCancelledReservations() { fixture(); assertEquals(20L, service.getBookings(7L,"UPCOMING").get(0).id()); assertEquals(19L, service.getBookings(7L,"COMPLETED").get(0).id()); }
    @Test void sameDayDeparturesAreClassifiedUsingTheTime() {
        var departed=booking(1L,Booking.BookingStatus.CONFIRMED,LocalDate.now()); departed.getSchedule().setDepartureTime(LocalTime.now().minusSeconds(1));
        when(bookings.findByPassengerIdOrderByBookedAtDescIdDesc(7L)).thenReturn(List.of(departed)); when(tickets.findByBookingIdIn(anyList())).thenReturn(List.of());
        assertEquals(1, service.getBookings(7L,"COMPLETED").size()); assertTrue(service.getBookings(7L,"UPCOMING").isEmpty());
    }
    @Test void unknownFilterBecomesAllAndEmptyResultsNeedNoTicketQuery() {
        when(bookings.findByPassengerIdOrderByBookedAtDescIdDesc(7L)).thenReturn(List.of());
        assertTrue(service.getBookings(7L,"???").isEmpty()); assertEquals("ALL", BookingHistoryService.normalizeFilter("???")); verifyNoInteractions(tickets);
    }
    @Test void anonymousHistoryDoesNotFallBackToADemoPassenger() {
        var passengers=mock(PassengerService.class); var controller=new PassengerController(passengers, bookings, service);
        assertEquals("redirect:/login", controller.showBookingHistory(null,null,new ExtendedModelMap())); verifyNoInteractions(passengers,bookings,tickets);
    }
    @Test void historyOnlyQueriesTheAuthenticatedPassenger() {
        var passengers=mock(PassengerService.class); when(passengers.getUserByEmail("own@test.invalid")).thenReturn(User.builder().id(7L).build());
        when(bookings.findByPassengerIdOrderByBookedAtDescIdDesc(7L)).thenReturn(List.of());
        var model=new ExtendedModelMap(); new PassengerController(passengers,bookings,service).showBookingHistory("pending",()->"own@test.invalid",model);
        assertEquals("PENDING",model.get("selectedFilter")); verify(bookings).findByPassengerIdOrderByBookedAtDescIdDesc(7L);
    }

    @Test void aPendingCancelledServiceNeverOffersContinuePayment() {
        var rows=fixture(); rows.get(0).getSchedule().getTrain().setStatus(com.trainbooking.it25102327.model.Train.TrainStatus.CANCELLED);
        var item=service.getBookings(7L,"CANCELLED").stream().filter(r->r.id().equals(24L)).findFirst().orElseThrow();
        assertEquals("Service cancelled",item.getStatusLabel()); assertEquals("View reservation",item.getActionLabel());
        assertTrue(service.getBookings(7L,"PENDING").isEmpty());
    }

}
