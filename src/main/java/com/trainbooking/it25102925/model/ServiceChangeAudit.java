package com.trainbooking.it25102925.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

/** Immutable operational evidence; identifiers and text snapshots survive unused-plan deletion. */
@Entity @Data @Builder @NoArgsConstructor @AllArgsConstructor @Table(name="service_change_audit")
public class ServiceChangeAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true,nullable=false,length=40) private String eventId;
    private Long scheduleId;
    private LocalDate serviceDate;
    private String previousStatus;
    private String newStatus;
    private Integer delayMinutes;
    @Column(length=300) private String reason;
    @Column(length=254) private String actor;
    private LocalDateTime changedAt;
}
