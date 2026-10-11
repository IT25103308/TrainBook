package com.trainbooking.it25100228.model;

import com.trainbooking.it25100977.model.Ticket;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity recording every QR ticket gate scan attempt for station audit trails and live operational analytics.
 *
 * @author SLIIT Software Engineering Team (IT25100228)
 * @version 1.0.0
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "boarding_logs")
public class BoardingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Column(nullable = false)
    private String scannedTicketNumber;

    @Builder.Default
    @Column(nullable = false)
    private String stationCode = "Not recorded";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private ScanResult scanResult;

    @Column(length=254) private String operatorEmail;
    @Column(length=300) private String detail;
    @Column(length=254) private String archivedBy;
    private LocalDateTime archivedAt;
    @Builder.Default private Boolean archived=false;
    private Integer passengerCount;
    private Long scheduleId;
    private java.time.LocalDate serviceDate;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime scannedAt;

    /**
     * Outcome of station gate QR code validation scan.
     */
    public enum ScanResult {
        VALID,
        DUPLICATE,
        CANCELLED,
        NOT_FOUND, MALFORMED, UNPAID, WRONG_SERVICE, WRONG_DATE, UNAVAILABLE, OUTSIDE_WINDOW, INVALID_ALLOCATION, CORRECTED
    }
}
