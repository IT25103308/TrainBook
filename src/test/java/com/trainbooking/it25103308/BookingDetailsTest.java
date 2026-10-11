package com.trainbooking.it25103308;

import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25103308.dto.BookingDetailsRequest;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25103308.service.BookingService;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingDetailsTest {
    BookingRepository repository; BookingService service; Booking booking;
    @BeforeEach void setup() {
        repository=mock(BookingRepository.class);
        service=new BookingService(repository,mock(ScheduleRepository.class),mock(UserRepository.class), null);
        LocalDate date=LocalDate.now().plusDays(1);
        booking=Booking.builder().id(1L).passenger(User.builder().email("own@test.invalid").build())
                .schedule(Schedule.builder().id(2L).train(Train.builder().status(Train.TrainStatus.ON_TIME).build())
                    .dayOfWeek(date.getDayOfWeek()).departureTime(LocalTime.of(10,0)).build())
                .travelDate(date).seatClass(Booking.SeatClass.FIRST).numberOfSeats(2).seatNumbers("Car 01 / S01, Car 01 / S02")
                .totalAmount(new BigDecimal("3000.00")).lockExpiresAt(LocalDateTime.now().plusMinutes(3))
                .status(Booking.BookingStatus.PENDING).passengerName("Old Name").build();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(booking));
        when(repository.save(any())).thenAnswer(i->i.getArgument(0));
    }
    private BookingDetailsRequest form() {
        var r=new BookingDetailsRequest();r.setPassengerName("Corrected Passenger");r.setDocumentType("FOREIGN_PASSPORT");
        r.setPassengerNic("ab123456");r.setContactPhone("+94 77 123 4567");return r;
    }
    @Test void correctionNormalizesOnlyPassengerFieldsAndPreservesAllocationFareExpiry() {
        LocalDateTime expiry=booking.getLockExpiresAt();var schedule=booking.getSchedule();var date=booking.getTravelDate();
        service.updatePassengerDetails(1L,"OWN@test.invalid",form());
        assertEquals("Corrected Passenger",booking.getPassengerName());assertEquals("AB123456",booking.getPassengerNic());
        assertEquals(Booking.DocumentType.FOREIGN_PASSPORT,booking.getDocumentType());assertEquals("0771234567",booking.getContactPhone());
        assertEquals(expiry,booking.getLockExpiresAt());assertEquals(new BigDecimal("3000.00"),booking.getTotalAmount());
        assertSame(schedule,booking.getSchedule());assertEquals(date,booking.getTravelDate());assertEquals(2,booking.getNumberOfSeats());
        assertEquals("Car 01 / S01, Car 01 / S02",booking.getSeatNumbers());assertEquals(Booking.BookingStatus.PENDING,booking.getStatus());
    }
    @Test void invalidDetailsDoNotPartiallyUpdate() {
        var form=form();form.setContactPhone("123");
        assertThrows(IllegalArgumentException.class,()->service.updatePassengerDetails(1L,"own@test.invalid",form));
        assertEquals("Old Name",booking.getPassengerName());verify(repository,never()).save(any());
    }
    @Test void anotherPassengerCannotUpdateOrCancel() {
        assertThrows(ResourceNotFoundException.class,()->service.updatePassengerDetails(1L,"other@test.invalid",form()));
        assertThrows(ResourceNotFoundException.class,()->service.cancelPendingBooking(1L,"other@test.invalid"));
        verify(repository,never()).save(any());
    }
    @Test void expiredDetailsAreRejectedWithoutRenewingDeadline() {
        booking.setLockExpiresAt(LocalDateTime.now().minusSeconds(1));LocalDateTime expiry=booking.getLockExpiresAt();
        assertThrows(IllegalStateException.class,()->service.updatePassengerDetails(1L,"own@test.invalid",form()));
        assertEquals(expiry,booking.getLockExpiresAt());verify(repository,never()).save(any());
    }
    @Test void confirmedBookingCannotBeEditedOrReleasedByPendingActions() {
        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        assertThrows(IllegalStateException.class,()->service.updatePassengerDetails(1L,"own@test.invalid",form()));
        assertThrows(IllegalStateException.class,()->service.cancelPendingBooking(1L,"own@test.invalid"));
        assertEquals(Booking.BookingStatus.CONFIRMED,booking.getStatus());verify(repository,never()).save(any());
    }
    @Test void unavailableServiceCannotBeEditedButItsUnpaidHoldCanBeReleased() {
        booking.getSchedule().setIsMaintenanceBlocked(true);
        assertThrows(IllegalStateException.class,()->service.updatePassengerDetails(1L,"own@test.invalid",form()));
        assertEquals(Booking.CancellationReason.PASSENGER,service.cancelPendingBooking(1L,"own@test.invalid"));
        assertEquals(Booking.BookingStatus.CANCELLED,booking.getStatus());
    }
    @Test void repeatedCancellationPreservesExpiryReasonAndDoesNotSaveAgain() {
        booking.setStatus(Booking.BookingStatus.CANCELLED);booking.setCancellationReason(Booking.CancellationReason.HOLD_EXPIRED);
        assertEquals(Booking.CancellationReason.HOLD_EXPIRED,service.cancelPendingBooking(1L,"own@test.invalid"));
        verify(repository,never()).save(any());
    }
}
