package com.trainbooking.it25103308;

import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.model.Train;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25103308.dto.BookingRequest;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25103308.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Lock-order and lifecycle unit checks; actual contention is in BookingPersistenceTest. */
class BookingConcurrencyTest {
    private BookingRepository bookings;
    private ScheduleRepository schedules;
    private UserRepository users;
    private BookingService service;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingRepository.class);
        schedules = mock(ScheduleRepository.class);
        users = mock(UserRepository.class);
        service = new BookingService(bookings, schedules, users, null);
    }

    @Test
    void locksScheduleBeforeReadingPassengerOrSeatInventory() {
        LocalDate date = LocalDate.now().plusDays(1);
        Train train = Train.builder().firstClassSeats(10).status(Train.TrainStatus.ACTIVE).build();
        Schedule schedule = Schedule.builder().id(50L).train(train).departureTime(java.time.LocalTime.of(10,0)).arrivalTime(java.time.LocalTime.NOON).dayOfWeek(date.getDayOfWeek())
                .firstClassFare(new BigDecimal("1500.00")).build();
        when(schedules.findByIdForUpdate(50L)).thenReturn(Optional.of(schedule));
        when(users.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).build()));
        when(bookings.save(any())).thenAnswer(call -> call.getArgument(0));
        BookingRequest request = new BookingRequest(50L, date, "FIRST", 1);
        request.setSeatNumbers("Car 01 / S03");
        request.setPassengerName("Test Passenger"); request.setDocumentType("NIC");
        request.setPassengerNic("200300901022"); request.setContactPhone("0771234567");

        service.createBooking(request, 1L);

        var order = inOrder(schedules, users, bookings);
        order.verify(schedules).findByIdForUpdate(50L);
        order.verify(users).findById(1L);
        order.verify(bookings).findByScheduleIdAndTravelDateAndSeatClassAndStatusNot(
                50L, date, Booking.SeatClass.FIRST, Booking.BookingStatus.CANCELLED);
        order.verify(bookings).save(any(Booking.class));
    }

    @Test
    void cleanupReleasesExpiredPendingHoldsOnly() {
        Booking expired = Booking.builder().id(1L).status(Booking.BookingStatus.PENDING)
                .lockExpiresAt(LocalDateTime.now().minusMinutes(1)).build();
        Booking active = Booking.builder().id(2L).status(Booking.BookingStatus.PENDING)
                .lockExpiresAt(LocalDateTime.now().plusMinutes(5)).build();
        Booking confirmed = Booking.builder().id(3L).status(Booking.BookingStatus.CONFIRMED)
                .lockExpiresAt(LocalDateTime.now().minusMinutes(1)).build();
        // Candidates may have been paid or extended before cleanup obtains their locks.
        when(bookings.findExpiredHoldIds(eq(Booking.BookingStatus.PENDING), any())).thenReturn(List.of(1L, 2L, 3L));
        when(bookings.findByIdForUpdate(1L)).thenReturn(Optional.of(expired));
        when(bookings.findByIdForUpdate(2L)).thenReturn(Optional.of(active));
        when(bookings.findByIdForUpdate(3L)).thenReturn(Optional.of(confirmed));
        service.releaseExpiredLocks();
        assertEquals(Booking.BookingStatus.CANCELLED, expired.getStatus());
        assertEquals(Booking.BookingStatus.PENDING, active.getStatus());
        assertEquals(Booking.BookingStatus.CONFIRMED, confirmed.getStatus());
        verify(bookings).save(expired);
        verify(bookings, never()).save(active);
        verify(bookings, never()).save(confirmed);
    }

    @Test
    void explicitReleaseCancelsPendingReservation() {
        Booking pending = Booking.builder().id(10L).status(Booking.BookingStatus.PENDING).build();
        when(bookings.findByIdForUpdate(10L)).thenReturn(Optional.of(pending));
        service.releaseLock(10L);
        assertEquals(Booking.BookingStatus.CANCELLED, pending.getStatus());
        verify(bookings).save(pending);
    }

    @Test
    void explicitReleaseDoesNotCancelConfirmedReservation() {
        Booking confirmed = Booking.builder().id(10L).status(Booking.BookingStatus.CONFIRMED).build();
        when(bookings.findByIdForUpdate(10L)).thenReturn(Optional.of(confirmed));
        service.releaseLock(10L);
        assertEquals(Booking.BookingStatus.CONFIRMED, confirmed.getStatus());
        verify(bookings, never()).save(any());
    }
}
