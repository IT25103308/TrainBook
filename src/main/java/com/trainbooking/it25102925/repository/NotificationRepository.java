package com.trainbooking.it25102925.repository;
import com.trainbooking.it25102925.model.*;
import com.trainbooking.it25102925.dto.*;

import com.trainbooking.it25102925.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Notification} entity management.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByEventIdAndBookingId(String eventId,Long bookingId);
    java.util.Optional<Notification> findByIdAndRecipientId(Long id,Long recipientId);
    @org.springframework.data.jpa.repository.Query("select n from Notification n where n.recipient.id=:userId order by n.sentAt desc,n.id desc")
    List<Notification> findOwned(@org.springframework.data.repository.query.Param("userId") Long userId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Notification n set n.emailResult=:result where n.id=:id")
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    int updateEmailResult(@org.springframework.data.repository.query.Param("id")Long id,@org.springframework.data.repository.query.Param("result")String result);

    /**
     * Retrieves all notifications sent to a recipient ordered by sent timestamp descending.
     *
     * @param userId the recipient user ID
     * @return ordered list of notifications
     */
    List<Notification> findByRecipientIdOrderBySentAtDesc(Long userId);

    /**
     * Counts the number of notifications matching read/unread status for a user.
     *
     * @param userId the recipient user ID
     * @param isRead the read status
     * @return count of matching notifications
     */
    long countByRecipientIdAndIsRead(Long userId, Boolean isRead);
}
