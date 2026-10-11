package com.trainbooking.it25100977.repository;

import com.trainbooking.it25100977.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Ticket} entity management.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    List<Ticket> findByBookingScheduleIdAndBookingTravelDate(Long scheduleId,java.time.LocalDate date);

    // Update only delivery metadata; a concurrent boarding flag must not be overwritten.
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Ticket t set t.emailDeliveryStatus = :state where t.id = :id")
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    int updateDeliveryStatus(@org.springframework.data.repository.query.Param("id") Long id,
                             @org.springframework.data.repository.query.Param("state") String state);

    /**
     * Finds a ticket by its unique ticket number string.
     *
     * @param ticketNumber unique ticket number
     * @return an {@link Optional} containing the ticket if found
     */
    Optional<Ticket> findByTicketNumber(String ticketNumber);

    /**
     * Finds a ticket issued for a specific booking ID.
     *
     * @param bookingId the booking ID
     * @return an {@link Optional} containing the ticket if found
     */
    Optional<Ticket> findByBookingId(Long bookingId);

    List<Ticket> findByBookingIdIn(List<Long> bookingIds);

    /**
     * Finds all tickets issued for a specific booking ID.
     *
     * @param bookingId the booking ID
     * @return list of tickets
     */
    java.util.List<Ticket> findAllByBookingId(Long bookingId);

    /**
     * Finds a ticket by its unique ticket number string, ignoring case.
     *
     * @param ticketNumber unique ticket number
     * @return an {@link Optional} containing the ticket if found
     */
    Optional<Ticket> findByTicketNumberIgnoreCase(String ticketNumber);

    /**
     * Returns the 10 most recently issued tickets.
     *
     * @return list of recent tickets
     */
    List<Ticket> findTop10ByOrderByIdDesc();

    /**
     * Deletes tickets associated with a given booking ID.
     *
     * @param bookingId booking ID
     */
    void deleteByBookingId(Long bookingId);
}
