package com.trainbooking.it25101520.repository;

import com.trainbooking.it25101520.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PasswordResetToken} entity management.
 *
 * @author SLIIT Software Engineering Team (IT25101520)
 * @version 1.0.0
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Finds a token entity by token string.
     *
     * @param token token string
     * @return optional containing the token if present
     */
    Optional<PasswordResetToken> findByToken(String token);

    /**
     * Deletes existing tokens associated with a user.
     *
     * @param userId user ID
     */
    void deleteByUserId(Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from PasswordResetToken t where t.token = :token")
    Optional<PasswordResetToken> findByTokenForUpdate(@org.springframework.data.repository.query.Param("token") String token);
}
