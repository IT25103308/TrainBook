package com.trainbooking.it25102925.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;

/** Retained transfer history; a replacement does not erase the original paid journey. */
@Entity @Data @Builder @NoArgsConstructor @AllArgsConstructor @Table(name="rebooking_transfers")
public class RebookingTransfer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long bookingId;
    @Column(unique=true) private Long tokenId;
    private Long fromScheduleId;
    private Long toScheduleId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String fromTrain;
    private String toTrain;
    private String origin;
    private String destination;
    private String fromSeats;
    private String toSeats;
    private BigDecimal paidAmount;
    private LocalDateTime transferredAt;
}
