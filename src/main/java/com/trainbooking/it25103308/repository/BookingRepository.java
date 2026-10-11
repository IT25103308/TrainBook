package com.trainbooking.it25103308.repository;

import com.trainbooking.it25103308.dto.*;
import com.trainbooking.it25103308.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

/**
 * Spring Data JPA repository for {@link Booking} entity management.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    // IDs only: do not cache stale booking entities before acquiring their locks.
    @Query("SELECT b.id FROM Booking b WHERE b.status = :status " +
            "AND b.lockExpiresAt <= :now ORDER BY b.id")
    List<Long> findExpiredHoldIds(@Param("status") Booking.BookingStatus status,
                                  @Param("now") LocalDateTime now);

    /**
     * Finds all bookings for a given passenger ID.
     *
     * @param passengerId the user ID of the passenger
     * @return list of bookings
     */
    List<Booking> findByPassengerId(Long passengerId);

    List<Booking> findByPassengerIdOrderByBookedAtDescIdDesc(Long passengerId);

    List<Booking> findByScheduleIdAndTravelDateAndSeatClassAndStatusNot(
            Long scheduleId, LocalDate travelDate, Booking.SeatClass seatClass, Booking.BookingStatus status);

    /**
     * Counts booked seats for a schedule on a specific travel date and seat class.
     *
     * @param scheduleId the schedule ID
     * @param travelDate the date of travel
     * @param seatClass the seat class (FIRST, SECOND)
     * @return total booked seats count
     */
    @Query("SELECT COALESCE(SUM(b.numberOfSeats), 0) FROM Booking b " +
           "WHERE b.schedule.id = :scheduleId " +
           "AND b.travelDate = :travelDate " +
           "AND b.seatClass = :seatClass " +
           "AND b.status <> 'CANCELLED'")
    Integer countByScheduleIdAndTravelDateAndSeatClass(
            @Param("scheduleId") Long scheduleId,
            @Param("travelDate") LocalDate travelDate,
            @Param("seatClass") Booking.SeatClass seatClass
    );

    /**
     * Finds all bookings attached to a specific schedule ID.
     *
     * @param scheduleId schedule ID
     * @return list of bookings
     */
    List<Booking> findByScheduleId(Long scheduleId);

    /**
     * Deletes all bookings referencing a schedule ID.
     *
     * @param scheduleId schedule ID
     */
    void deleteByScheduleId(Long scheduleId);
}
