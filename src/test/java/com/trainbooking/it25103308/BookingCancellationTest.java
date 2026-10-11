package com.trainbooking.it25103308;

import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25103308.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingCancellationTest {
    private BookingRepository repository;
    private BookingService service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        repository = mock(BookingRepository.class);
        service = new BookingService(repository, mock(ScheduleRepository.class), mock(UserRepository.class), null);
        booking = Booking.builder().id(12L).status(Booking.BookingStatus.PENDING)
                .lockExpiresAt(LocalDateTime.now().plusMinutes(5)).build();
        when(repository.findByIdForUpdate(12L)).thenReturn(Optional.of(booking));
    }

    @Test
    void cancellationBeforeDeadlineRecordsPassengerAndPreservesDeadline() {
        LocalDateTime expiry = booking.getLockExpiresAt();
        service.releaseLock(12L);
        assertEquals(Booking.BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(Booking.CancellationReason.PASSENGER, booking.getCancellationReason());
        assertEquals(expiry, booking.getLockExpiresAt());
        verify(repository).save(booking);
    }

    @Test
    void clickingCancelAfterDeadlineRecordsExpiryInsteadOfClaimingPassengerCancelledInTime() {
        booking.setLockExpiresAt(LocalDateTime.now().minusSeconds(1));
        service.releaseLock(12L);
        assertEquals(Booking.CancellationReason.HOLD_EXPIRED, booking.getCancellationReason());
    }

    @Test
    void cleanupRecordsExpiryAndPreservesOriginalDeadline() {
        LocalDateTime expiry = LocalDateTime.now().minusSeconds(1);
        booking.setLockExpiresAt(expiry);
        when(repository.findExpiredHoldIds(eq(Booking.BookingStatus.PENDING), any())).thenReturn(List.of(12L));
        service.releaseExpiredLocks();
        assertEquals(Booking.BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(Booking.CancellationReason.HOLD_EXPIRED, booking.getCancellationReason());
        assertEquals(expiry, booking.getLockExpiresAt());
    }

    @Test
    void repeatedCancelDoesNotRewriteAnExistingExpiryReason() {
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        booking.setCancellationReason(Booking.CancellationReason.HOLD_EXPIRED);
        service.releaseLock(12L);
        assertEquals(Booking.CancellationReason.HOLD_EXPIRED, booking.getCancellationReason());
        verify(repository, never()).save(any());
    }

    @Test
    void cleanupDoesNotRewriteAPassengerCancellationWhileWaitingForLock() {
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        booking.setCancellationReason(Booking.CancellationReason.PASSENGER);
        when(repository.findExpiredHoldIds(eq(Booking.BookingStatus.PENDING), any())).thenReturn(List.of(12L));
        service.releaseExpiredLocks();
        assertEquals(Booking.CancellationReason.PASSENGER, booking.getCancellationReason());
        verify(repository, never()).save(any());
    }

    @Test
    void confirmedBookingCannotAcquireACancellationReasonThroughHoldCancellation() {
        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        service.releaseLock(12L);
        assertNull(booking.getCancellationReason());
        assertEquals(Booking.BookingStatus.CONFIRMED, booking.getStatus());
        verify(repository, never()).save(any());
    }
}
