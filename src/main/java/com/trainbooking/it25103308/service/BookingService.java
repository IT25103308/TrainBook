package com.trainbooking.it25103308.service;

import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.model.Train;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25103308.dto.BookingRequest;
import com.trainbooking.it25103308.dto.CheckoutRecovery;
import com.trainbooking.it25103308.dto.SeatAvailability;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Service class handling booking lifecycle, available seat calculations, and reservation management.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    private final jakarta.persistence.EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.trainbooking.it25100977.repository.PaymentRepository paymentRepository;

    private void cancelUnsettledPayment(Long bookingId) {
        if (paymentRepository != null) paymentRepository.cancelUnsettled(bookingId,
                com.trainbooking.it25100977.model.Payment.PaymentStatus.CANCELLED,
                com.trainbooking.it25100977.model.Payment.PaymentStatus.COMPLETED);
    }

    @Transactional(readOnly = true)
    public com.trainbooking.it25100977.dto.PaymentAttemptView getPaymentAttempt(Long bookingId) {
        if (paymentRepository == null) return null;
        return paymentRepository.findByBookingId(bookingId).map(p -> new com.trainbooking.it25100977.dto.PaymentAttemptView(
                p.getStatus().name(), p.getTransactionRef(), p.getAttemptedAt())).orElse(null);
    }

    private void refreshLocked(Object entity) {
        if (entityManager == null) return;
        entityManager.refresh(entity);
        if (entity instanceof Schedule schedule) {
            if (schedule.getTrain() != null) entityManager.refresh(schedule.getTrain());
            if (schedule.getRoute() != null) entityManager.refresh(schedule.getRoute());
        }
    }

    /**
     * Calculates the number of available seats remaining for a schedule, date, and seat class.
     *
     * @param scheduleId schedule ID
     * @param date date of travel
     * @param seatClass FIRST or SECOND seat class
     * @return count of available seats
     */
    @Transactional(readOnly = true)
    public int getAvailableSeats(Long scheduleId, LocalDate date, String seatClass) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null || schedule.getTrain() == null) {
            return 0;
        }
        return readAvailability(schedule, date, resolveClass(seatClass)).availableSeats();
    }

    @Transactional(readOnly = true)
    public SeatAvailability getSeatAvailability(Long scheduleId, LocalDate date, String seatClass) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with ID: " + scheduleId));
        return readAvailability(schedule, date, resolveClass(seatClass));
    }

    private SeatAvailability readAvailability(Schedule schedule, LocalDate date, Booking.SeatClass seatClass) {
        if (date == null) {
            throw new IllegalArgumentException("Please choose a travel date.");
        }
        int capacity = capacityOf(schedule, seatClass);
        List<Booking> bookings = bookingRepository.findByScheduleIdAndTravelDateAndSeatClassAndStatusNot(
                schedule.getId(), date, seatClass, Booking.BookingStatus.CANCELLED);
        Set<String> occupied = new LinkedHashSet<>();
        long reservedQuantity = 0;
        boolean inconsistent = false;
        LocalDateTime now = LocalDateTime.now();
        // Expiry is authoritative even before cleanup persists CANCELLED. Payment
        // locks the same schedule and rechecks its original deadline after waiting.
        for (Booking booking : bookings) {
            if (booking.getStatus() == Booking.BookingStatus.PENDING
                    && booking.getLockExpiresAt() != null && !booking.getLockExpiresAt().isAfter(now)) continue;
            Integer quantity = booking.getNumberOfSeats();
            if (quantity == null || quantity < 1) {
                inconsistent = true;
                continue;
            }
            reservedQuantity += quantity;
            try {
                List<String> seats = SeatSelection.parseStored(booking.getSeatNumbers(), seatClass, capacity);
                if (seats.size() != quantity) {
                    inconsistent = true;
                }
                for (String seat : seats) {
                    if (!occupied.add(seat)) {
                        inconsistent = true;
                    }
                }
            } catch (IllegalArgumentException ex) {
                inconsistent = true;
            }
        }
        if (inconsistent || reservedQuantity > capacity) {
            return new SeatAvailability(capacity, 0,
                    IntStream.rangeClosed(1, capacity).mapToObj(n -> SeatSelection.label(seatClass, n)).toList(),
                    "Existing seat records for this class and date need staff review. Please choose another class or service.");
        }
        return new SeatAvailability(capacity, capacity - (int) reservedQuantity, List.copyOf(occupied), null);
    }

    private int capacityOf(Schedule schedule, Booking.SeatClass seatClass) {
        Train train = schedule.getTrain();
        Integer capacity = train == null ? null : seatClass == Booking.SeatClass.FIRST
                ? train.getFirstClassSeats() : train.getSecondClassSeats();
        return capacity == null ? 0 : Math.max(0, capacity);
    }

    private Booking.SeatClass resolveClass(String value) {
        try {
            return Booking.SeatClass.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Please choose FIRST or SECOND class.");
        }
    }

    /**
     * Creates a new pending train booking for a passenger with a 10-minute temporary lock.
     *
     * @param request booking details
     * @param userId ID of the passenger making the reservation
     * @return created {@link Booking} entity
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(BookingRequest request, Long userId) {
        if (request == null || request.getScheduleId() == null || request.getScheduleId() < 1) {
            throw new IllegalArgumentException("Please choose a valid train service.");
        }
        LocalDate travelDate = request.getTravelDate();
        if (travelDate == null || travelDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Please choose today or a future travel date.");
        }
        Booking.SeatClass seatClass = resolveClass(request.getSeatClass());
        Integer requestedSeats = request.getNumberOfSeats();
        if (requestedSeats == null || requestedSeats < 1 || requestedSeats > 6) {
            throw new IllegalArgumentException("Choose between 1 and 6 seats.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("Please sign in before reserving seats.");
        }

        PassengerDetails details = PassengerDetails.validate(request);

        // The database holds this lock until commit, across application instances.
        // All new reservation checks happen after it is acquired.
        Schedule schedule = scheduleRepository.findByIdForUpdate(request.getScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with ID: " + request.getScheduleId()));
        refreshLocked(schedule);
        var plan=com.trainbooking.it25102327.service.ServicePlan.resolve(schedule,travelDate);
        if(plan.arrival()==null || !plan.availableAt(LocalDateTime.now())) throw new IllegalArgumentException("This service is not available on the selected date or has already departed.");
        User passenger = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger account not found."));
        SeatAvailability availability = readAvailability(schedule, travelDate, seatClass);
        if (availability.warning() != null) {
            throw new IllegalStateException(availability.warning());
        }
        List<String> selectedSeats = SeatSelection.parseRequested(
                request.getSeatNumbers(), seatClass, availability.capacity(), requestedSeats);
        if (availability.availableSeats() < requestedSeats) {
            throw new IllegalStateException("Not enough seats remain. Please select your seats again.");
        }
        if (selectedSeats.stream().anyMatch(availability.occupiedSeats()::contains)) {
            throw new IllegalStateException("One or more selected seats are already held or booked. Please choose other seats.");
        }
        BigDecimal perSeatFare;
        try { perSeatFare=plan.fare(seatClass.name()); }
        catch(IllegalArgumentException invalidFare) { throw new IllegalStateException("The fare for this service needs staff review.",invalidFare); }
        BigDecimal totalAmount = perSeatFare.multiply(BigDecimal.valueOf(requestedSeats));
        Booking booking = Booking.builder()
                .passenger(passenger)
                .schedule(schedule)
                .travelDate(travelDate)
                .seatClass(seatClass)
                .numberOfSeats(requestedSeats)
                .totalAmount(totalAmount)
                .departureTimeSnapshot(plan.departure()).arrivalTimeSnapshot(plan.arrival()).arrivalDayOffsetSnapshot(plan.arrivalDayOffset())
                .seatNumbers(String.join(", ", selectedSeats))
                .passengerName(details.name())
                .documentType(details.documentType())
                .passengerNic(details.documentNumber())
                .contactPhone(details.phone())
                .lockExpiresAt(LocalDateTime.now().plusMinutes(10))
                .status(Booking.BookingStatus.PENDING)
                .build();

        Booking saved = bookingRepository.save(booking);
        log.info("Booking created successfully with ID: {} and total: LKR {}", saved.getId(), saved.getTotalAmount());
        return saved;
    }

    /**
     * Retrieves booking details by ID.
     *
     * @param id booking ID
     * @return {@link Booking} entity
     */
    public Booking getBookingById(Long id) {
        log.info("Fetching booking by ID: {}", id);
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public CheckoutRecovery getCheckoutRecovery(Long id) {
        // Load the associated schedule/train inside the transaction, including without Open-In-View.
        return CheckoutRecovery.from(getBookingById(id), LocalDateTime.now());
    }

    /**
     * Explicitly releases a passenger's pending seat lock upon checkout cancellation or abandonment.
     *
     * @param bookingId booking ID
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void releaseLock(Long bookingId) {
        log.info("Releasing seat lock for booking ID: {}", bookingId);
        bookingRepository.findByIdForUpdate(bookingId).ifPresent(b -> {
            refreshLocked(b);
            if (b.getStatus() == Booking.BookingStatus.PENDING) {
                b.setCancellationReason(b.getLockExpiresAt() != null
                        && !b.getLockExpiresAt().isAfter(LocalDateTime.now())
                        ? Booking.CancellationReason.HOLD_EXPIRED : Booking.CancellationReason.PASSENGER);
                b.setStatus(Booking.BookingStatus.CANCELLED);
                bookingRepository.save(b);
                cancelUnsettledPayment(b.getId());
                log.info("Seat lock released back to inventory for booking ID: {}", bookingId);
            }
        });
    }

    /** Passenger correction is serialized with payment/cancellation and never renews a hold. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking updatePassengerDetails(Long id, String ownerEmail,
            com.trainbooking.it25103308.dto.BookingDetailsRequest request) {
        Booking booking = lockOwnedBooking(id, ownerEmail);
        if (!com.trainbooking.it25103308.dto.CheckoutHold.from(booking, LocalDateTime.now()).isPayable()) {
            throw new IllegalStateException("Passenger details can only be corrected on an active, unpaid seat hold. Paid, expired or cancelled reservations cannot be edited.");
        }
        if (request == null) throw new IllegalArgumentException("Enter the passenger details.");
        PassengerDetails details = PassengerDetails.validate(request.validatedFields());
        booking.setPassengerName(details.name());
        booking.setDocumentType(details.documentType());
        booking.setPassengerNic(details.documentNumber());
        booking.setContactPhone(details.phone());
        return bookingRepository.save(booking);
    }

    /** Logical deletion of an unpaid hold; paid transactions require a separate lifecycle. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking.CancellationReason cancelPendingBooking(Long id, String ownerEmail) {
        Booking booking = lockOwnedBooking(id, ownerEmail);
        if (booking.getStatus() == Booking.BookingStatus.CONFIRMED) {
            throw new IllegalStateException("This reservation is already paid. It cannot be cancelled using the unpaid seat-hold action.");
        }
        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) return booking.getCancellationReason();
        if (booking.getStatus() != Booking.BookingStatus.PENDING) {
            throw new IllegalStateException("This reservation cannot be cancelled.");
        }
        booking.setCancellationReason(booking.getLockExpiresAt() != null
                && !booking.getLockExpiresAt().isAfter(LocalDateTime.now())
                ? Booking.CancellationReason.HOLD_EXPIRED : Booking.CancellationReason.PASSENGER);
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        cancelUnsettledPayment(booking.getId());
        return booking.getCancellationReason();
    }

    private Booking lockOwnedBooking(Long id, String ownerEmail) {
        if (id == null || id < 1) throw new ResourceNotFoundException("Reservation not found.");
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found."));
        refreshLocked(booking);
        BookingAccess.requireOwner(booking, ownerEmail == null ? null : () -> ownerEmail);
        return booking;
    }

    /**
     * Automated background worker that periodically scans for and auto-cancels unconfirmed seat locks
     * that have exceeded their 10-minute temporary reservation window.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 30000)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void releaseExpiredLocks() {
        LocalDateTime now = LocalDateTime.now();
        for (Long bookingId : bookingRepository.findExpiredHoldIds(Booking.BookingStatus.PENDING, now)) {
            bookingRepository.findByIdForUpdate(bookingId).ifPresent(b -> {
                refreshLocked(b);
                // A payment may have completed while cleanup waited for the row lock.
                if (b.getStatus() == Booking.BookingStatus.PENDING && b.getLockExpiresAt() != null
                        && !b.getLockExpiresAt().isAfter(now)) {
                    b.setCancellationReason(Booking.CancellationReason.HOLD_EXPIRED);
                    b.setStatus(Booking.BookingStatus.CANCELLED);
                    bookingRepository.save(b);
                    cancelUnsettledPayment(b.getId());
                    log.info("Auto-released expired lock for booking #{}", b.getId());
                }
            });
        }
    }

    /**
     * Returns a list of occupied seat numbers (e.g. "S01", "S02") for visual seat map rendering.
     */
    @Transactional(readOnly = true)
    public List<String> getBookedSeatNumbers(Long scheduleId, LocalDate travelDate, String seatClass) {
        return getSeatAvailability(scheduleId, travelDate, seatClass).occupiedSeats();
    }
}
