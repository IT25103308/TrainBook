package com.trainbooking.it25103308;

import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.model.Train;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25103308.dto.BookingRequest;
import com.trainbooking.it25103308.dto.SeatAvailability;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25103308.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pure unit tests: no Spring context, application, or database is started. */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock private BookingRepository bookingRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private BookingService bookingService;

    private LocalDate travelDate;
    private User passenger;
    private Train train;
    private Schedule schedule;

    @BeforeEach
    void setUp() {
        travelDate = LocalDate.now().plusDays(2);
        passenger = User.builder().id(1L).firstName("Anfas").lastName("Passenger")
                .email("anfas@example.invalid").phone("0771234567").build();
        train = Train.builder().id(2L).firstClassSeats(12).secondClassSeats(24)
                .status(Train.TrainStatus.ON_TIME).build();
        schedule = Schedule.builder().id(10L).train(train).departureTime(java.time.LocalTime.of(10,0)).arrivalTime(java.time.LocalTime.NOON).dayOfWeek(travelDate.getDayOfWeek())
                .isActive(true).isMaintenanceBlocked(false)
                .firstClassFare(new BigDecimal("1500.00"))
                .secondClassFare(new BigDecimal("800.00")).build();
    }

    private BookingRequest request() {
        return BookingRequest.builder().scheduleId(10L).travelDate(travelDate).seatClass("FIRST")
                .seatCount(2).seatNumbers("Car 01 / S03, Car 01 / S07")
                .passengerName("Anfas Passenger").documentType("NIC").passengerNic("200300901022").contactPhone("0771234567")
                .build();
    }

    // Rejected requests can deliberately stop before either lookup.
    private void stubCreationLookups() {
        lenient().when(scheduleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(schedule));
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(passenger));
    }

    private void stubSave() {
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            booking.setId(100L);
            return booking;
        });
    }

    private Booking allocation(String labels, int quantity) {
        return Booking.builder().id(200L).schedule(schedule).travelDate(travelDate)
                .seatClass(Booking.SeatClass.FIRST).numberOfSeats(quantity).seatNumbers(labels)
                .status(Booking.BookingStatus.CONFIRMED).build();
    }

    private void stubInventory(Booking... bookings) {
        when(bookingRepository.findByScheduleIdAndTravelDateAndSeatClassAndStatusNot(
                10L, travelDate, Booking.SeatClass.FIRST, Booking.BookingStatus.CANCELLED))
                .thenReturn(List.of(bookings));
    }

    private void stubAvailability(Booking... bookings) {
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));
        stubInventory(bookings);
    }

    @Test
    void storesExactlyTheNonAdjacentSeatsThePassengerSelected() {
        stubCreationLookups();
        stubSave();
        LocalDateTime before = LocalDateTime.now();

        Booking saved = bookingService.createBooking(request(), 1L);

        assertEquals("Car 01 / S03, Car 01 / S07", saved.getSeatNumbers());
        assertEquals(2, saved.getNumberOfSeats());
        assertEquals(new BigDecimal("3000.00"), saved.getTotalAmount());
        assertEquals(travelDate, saved.getTravelDate());
        assertSame(passenger, saved.getPassenger());
        assertSame(schedule, saved.getSchedule());
        assertEquals("200300901022", saved.getPassengerNic());
        assertEquals(Booking.DocumentType.NIC, saved.getDocumentType());
        assertEquals(Booking.BookingStatus.PENDING, saved.getStatus());
        assertFalse(saved.getLockExpiresAt().isBefore(before.plusMinutes(10)));
        assertFalse(saved.getLockExpiresAt().isAfter(LocalDateTime.now().plusMinutes(10)));
        verify(bookingRepository).save(saved);
    }

    @Test
    void secondClassUsesItsOwnCapacityCoachAndServerFare() {
        stubCreationLookups();
        stubSave();
        BookingRequest request = request();
        request.setSeatClass("SECOND");
        request.setSeatNumbers("Car 02 / S13, Car 02 / S24");

        Booking saved = bookingService.createBooking(request, 1L);

        assertEquals(Booking.SeatClass.SECOND, saved.getSeatClass());
        assertEquals("Car 02 / S13, Car 02 / S24", saved.getSeatNumbers());
        assertEquals(new BigDecimal("1600.00"), saved.getTotalAmount());
        verify(bookingRepository).findByScheduleIdAndTravelDateAndSeatClassAndStatusNot(
                10L, travelDate, Booking.SeatClass.SECOND, Booking.BookingStatus.CANCELLED);
    }

    @Test
    void rejectsOccupiedSeatEvenWhenOtherSeatsRemain() {
        stubCreationLookups();
        stubInventory(allocation("Car 01 / S07", 1));

        assertThrows(IllegalStateException.class, () -> bookingService.createBooking(request(), 1L));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void legacyRangePreventsSelectingAnySeatInsideIt() {
        stubCreationLookups();
        stubInventory(allocation("Car 01 / S1 - S4", 4));

        assertThrows(IllegalStateException.class, () -> bookingService.createBooking(request(), 1L));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsWhenAClassIsFull() {
        stubCreationLookups();
        stubInventory(allocation("Car 01 / S1 - S12", 12));

        assertThrows(IllegalStateException.class, () -> bookingService.createBooking(request(), 1L));
        verify(bookingRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Car 01 / S03, Car 01 / S03", "Car 02 / S03, Car 02 / S07",
            "Car 01 / S03", "Car 01 / S03 - S04", "Car 01 / S00, Car 01 / S01",
            "Car 01 / S03, Car 01 / S13"})
    void rejectsInvalidSelectionsWithoutSaving(String labels) {
        stubCreationLookups();
        BookingRequest request = request();
        request.setSeatNumbers(labels);

        assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(request, 1L));
        verify(bookingRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 7})
    void rejectsMissingOrOutOfRangeQuantity(Integer quantity) {
        BookingRequest request = request();
        request.setSeatCount(quantity);

        assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(request, 1L));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsConflictingQuantityAliases() {
        BookingRequest request = request();
        request.setNumberOfSeats(1);

        assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(request, 1L));
        verify(bookingRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "THIRD"})
    void rejectsMissingOrUnknownClass(String seatClass) {
        BookingRequest request = request();
        request.setSeatClass(seatClass);

        assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(request, 1L));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsMissingTravelDate() {
        BookingRequest request = request();
        request.setTravelDate(null);

        assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(request, 1L));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsPastTravelDate() {
        BookingRequest request = request();
        request.setTravelDate(LocalDate.now().minusDays(1));

        assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(request, 1L));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsWrongScheduleDay() {
        stubCreationLookups();
        schedule.setDayOfWeek(travelDate.plusDays(1).getDayOfWeek());

        assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(request(), 1L));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsInactiveSchedule() {
        stubCreationLookups();
        schedule.setIsActive(false);

        assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(request(), 1L));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsMaintenanceBlockedSchedule() {
        stubCreationLookups();
        schedule.setIsMaintenanceBlocked(true);

        assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(request(), 1L));
        verify(bookingRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = Train.TrainStatus.class, names = {"CANCELLED", "MAINTENANCE", "INACTIVE"})
    void rejectsUnavailableTrain(Train.TrainStatus status) {
        stubCreationLookups();
        train.setStatus(status);

        assertThrows(IllegalArgumentException.class, () -> bookingService.createBooking(request(), 1L));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsNegativeFare() {
        stubCreationLookups();
        schedule.setFirstClassFare(new BigDecimal("-1.00"));

        assertThrows(IllegalStateException.class, () -> bookingService.createBooking(request(), 1L));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void unknownPassengerNeverFallsBackToDemoUser() {
        when(scheduleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(schedule));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(request(), 99L));
        verify(userRepository, never()).findByEmail(anyString());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void knownLegacyRangesProduceCanonicalOccupiedSeatsAndCorrectQuantity() {
        stubAvailability(allocation("Car 01 / S1 - S3", 3));

        SeatAvailability result = bookingService.getSeatAvailability(10L, travelDate, "FIRST");

        assertEquals(12, result.capacity());
        assertEquals(9, result.availableSeats());
        assertEquals(List.of("Car 01 / S01", "Car 01 / S02", "Car 01 / S03"), result.occupiedSeats());
        assertNull(result.warning());
    }

    @Test
    void queryKeepsInventoryScopedToScheduleTravelDateAndClass() {
        LocalDate otherDate = travelDate.plusDays(1);
        when(scheduleRepository.findById(10L)).thenReturn(Optional.of(schedule));

        SeatAvailability result = bookingService.getSeatAvailability(10L, otherDate, "SECOND");

        assertEquals(24, result.availableSeats());
        verify(bookingRepository).findByScheduleIdAndTravelDateAndSeatClassAndStatusNot(
                10L, otherDate, Booking.SeatClass.SECOND, Booking.BookingStatus.CANCELLED);
        verify(bookingRepository, never()).findAll();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Car 01 / Seat A12, A13", "Car 01 / S1 - S99"})
    void unidentifiedLegacyInventoryBlocksSalesWithoutChangingStoredData(String labels) {
        Booking existing = allocation(labels, 2);
        stubAvailability(existing);

        SeatAvailability result = bookingService.getSeatAvailability(10L, travelDate, "FIRST");

        assertEquals(0, result.availableSeats());
        assertNotNull(result.warning());
        assertFalse(result.warning().isBlank());
        assertEquals(labels, existing.getSeatNumbers());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void identityCountMismatchBlocksNewSales() {
        stubAvailability(allocation("Car 01 / S01", 2));

        SeatAvailability result = bookingService.getSeatAvailability(10L, travelDate, "FIRST");

        assertEquals(0, result.availableSeats());
        assertNotNull(result.warning());
    }

    @Test
    void overlappingExistingBookingsBlockNewSales() {
        stubAvailability(allocation("Car 01 / S01", 1), allocation("Car 01 / S01", 1));

        SeatAvailability result = bookingService.getSeatAvailability(10L, travelDate, "FIRST");

        assertEquals(0, result.availableSeats());
        assertNotNull(result.warning());
    }

    @Test
    void cannotCreateBookingOverUnidentifiedLegacyInventory() {
        stubCreationLookups();
        stubInventory(allocation(null, 1));

        assertThrows(IllegalStateException.class, () -> bookingService.createBooking(request(), 1L));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void expiredPendingSeatsAreAvailableBeforeCleanupWithoutMutatingHistory() {
        Booking expired = allocation("Car 01 / S03", 1);
        expired.setStatus(Booking.BookingStatus.PENDING);
        expired.setLockExpiresAt(LocalDateTime.now().minusMinutes(1));
        stubAvailability(expired);

        SeatAvailability result = bookingService.getSeatAvailability(10L, travelDate, "FIRST");

        assertEquals(12, result.availableSeats());
        assertEquals(List.of(), result.occupiedSeats());
        assertEquals(Booking.BookingStatus.PENDING, expired.getStatus());
    }

    @Test
    void missingScheduleHasNoAvailableSeats() {
        when(scheduleRepository.findById(10L)).thenReturn(Optional.empty());

        assertEquals(0, bookingService.getAvailableSeats(10L, travelDate, "FIRST"));
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void bookedSeatWrapperReturnsCanonicalIdentities() {
        stubAvailability(allocation("Car 01 / S1 - S2", 2));

        assertEquals(List.of("Car 01 / S01", "Car 01 / S02"),
                bookingService.getBookedSeatNumbers(10L, travelDate, "FIRST"));
    }
}

