package com.trainbooking.it25100977.repository;
import com.trainbooking.it25100977.model.*;
import com.trainbooking.it25100977.dto.*;

import com.trainbooking.it25100977.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Payment} entity management.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Payment p set p.status = :cancelled where p.booking.id = :id and p.status <> :completed")
    @org.springframework.transaction.annotation.Transactional
    int cancelUnsettled(@org.springframework.data.repository.query.Param("id") Long bookingId,
                       @org.springframework.data.repository.query.Param("cancelled") Payment.PaymentStatus cancelled,
                       @org.springframework.data.repository.query.Param("completed") Payment.PaymentStatus completed);

    /**
     * Finds payment record associated with a given booking ID.
     *
     * @param bookingId the booking ID
     * @return an {@link Optional} containing the payment if present
     */
    Optional<Payment> findByBookingId(Long bookingId);

    /**
     * Finds all payments associated with a given booking ID.
     *
     * @param bookingId booking ID
     * @return list of payments
     */
    List<Payment> findAllByBookingId(Long bookingId);

    /**
     * Deletes payments associated with a given booking ID.
     *
     * @param bookingId booking ID
     */
    void deleteByBookingId(Long bookingId);
}
