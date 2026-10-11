package com.trainbooking.it25102925.model;

import com.trainbooking.it25101520.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Entity representing an alert/notification sent to a system user.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notifications",uniqueConstraints=@jakarta.persistence.UniqueConstraint(columnNames={"event_id","booking_id"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(nullable = false)
    private String subject;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Builder.Default
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime sentAt;
    @Column(name="event_id",length=40) private String eventId;
    @Column(name="booking_id") private Long bookingId;
    private LocalDate serviceDate;
    private String recordedTrainNumber;
    @Column(length=200) private String actionUrl;
    @Column(length=32) private String emailResult;
    private Boolean smsSimulated;
    @Builder.Default private Boolean archived=false;

    public Boolean getRead() {
        return Boolean.TRUE.equals(isRead);
    }

    public Boolean isRead() {
        return Boolean.TRUE.equals(isRead);
    }

    public String getTitle() {
        return subject != null ? subject : "Train Schedule Notice";
    }

    public String getCreatedAt() {
        return sentAt != null ? sentAt.toString() : "";
    }

    public String getTrainNumber() {
        if(recordedTrainNumber!=null) return recordedTrainNumber;
        if (subject != null && subject.contains("(") && subject.contains(")")) {
            int start = subject.indexOf('(');
            int end = subject.indexOf(')');
            if (start != -1 && end > start) {
                return subject.substring(start + 1, end).trim();
            }
        }
        if (message != null && message.contains("Train #")) {
            int start = message.indexOf("Train #") + 7;
            int end = message.indexOf(" ", start);
            if (end != -1 && end > start) {
                return message.substring(start, end).trim();
            }
        }
        return "Not recorded";
    }

    public String getDisplayType() {
        String sub = (subject != null ? subject : "").toUpperCase();
        String msg = (message != null ? message : "").toUpperCase();
        if (sub.contains("CANCEL") || msg.contains("CANCEL")) return "CANCELLED";
        if (sub.contains("DELAY") || msg.contains("DELAY")) return "DELAY";
        if (sub.contains("BOOKING") || sub.contains("CONFIRM") || msg.contains("BOOKING")) return "BOOKING";
        if (type != null) {
            String name = type.name();
            if ("EMAIL".equals(name) || "SMS".equals(name) || "IN_APP".equals(name)) {
                return "SYSTEM";
            }
            return name;
        }
        return "SYSTEM";
    }

    /**
     * Types/channels of notifications dispatched.
     */
    public enum NotificationType {
        EMAIL,
        SMS,
        IN_APP,
        DELAY,
        CANCELLED,
        BOOKING,
        CONFIRMATION,
        SYSTEM
    }
}
