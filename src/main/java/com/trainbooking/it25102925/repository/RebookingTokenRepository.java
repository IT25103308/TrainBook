package com.trainbooking.it25102925.repository;

import com.trainbooking.it25102925.model.RebookingToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link RebookingToken} entity management.
 *
 * @author SLIIT Software Engineering Team (IT25102925)
 * @version 1.0.0
 */
@Repository
public interface RebookingTokenRepository extends JpaRepository<RebookingToken, Long> {

    java.util.List<RebookingToken> findByBookingIdAndIsRedeemedFalse(Long bookingId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from RebookingToken t where t.id=:id")
    Optional<RebookingToken> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    /**
     * Finds an unredeemed rebooking token by its secure string.
     *
     * @param token the unique token string
     * @return optional containing the active token if found
     */
    Optional<RebookingToken> findByTokenAndIsRedeemedFalse(String token);

    /**
     * Finds any token matching the token string regardless of redemption status.
     *
     * @param token the unique token string
     * @return optional containing the token
     */
    Optional<RebookingToken> findByToken(String token);
}
