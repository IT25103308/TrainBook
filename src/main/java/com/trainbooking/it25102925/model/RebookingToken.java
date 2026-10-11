package com.trainbooking.it25102925.model;

import com.trainbooking.it25103308.model.Booking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a secure, one-click compensation token allowing passengers
 * of cancelled train services to transfer to an alternative train without additional payment.
 *
 * @author SLIIT Software Engineering Team (IT25102925)
 * @version 1.0.0
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "rebooking_tokens")
public class RebookingToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false, unique = true)
    private String token;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isRedeemed = false;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    private Long sourceScheduleId;
    private java.time.LocalDate sourceDate;
    @Column(length=20) private String sourceClass;

    /**
     * Checks if this rebooking claim token has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return expiresAt==null || !expiresAt.isAfter(LocalDateTime.now());
    }
}
