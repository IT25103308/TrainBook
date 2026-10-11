package com.trainbooking.it25100977.model;

import com.trainbooking.it25103308.model.Booking;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing an electronic ticket (e-ticket) issued upon successful payment.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, unique = true)
    private String ticketNumber;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String qrCodeData;

    @Builder.Default
    private Boolean isBoarded = false;

    private LocalDateTime boardedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime issuedAt;

    @Column(length = 32, columnDefinition = "varchar(32)")
    private String emailDeliveryStatus;

    public String getEmailDeliveryMessage() {
        if (emailDeliveryStatus == null) return "Email delivery was not verified. Download or print your ticket.";
        return switch (emailDeliveryStatus) {
            case "DISABLED" -> "Email delivery is disabled for this local demo. Download or print your ticket.";
            case "SMTP_ACCEPTED" -> "Your ticket was submitted to the email server. Download remains available.";
            case "FAILED" -> "Your ticket is issued, but its email could not be sent. Download or print it here.";
            case "PENDING" -> "Ticket email has not been confirmed yet. You can download or print it now.";
            default -> "Email delivery was not verified. Download or print your ticket.";
        };
    }

    public String getTrainName() {
        return booking != null ? booking.getTrainName() : "Express Train";
    }

    public String getStatus() {
        if (Boolean.TRUE.equals(isBoarded)) {
            return "BOARDED";
        }
        return (booking != null && booking.getStatus() != null) ? booking.getStatus().name() : "CONFIRMED";
    }

    public String getPassengerName() {
        return booking != null ? booking.getPassengerName() : "Passenger";
    }

    public String getDocumentLabel() { return booking == null ? "Document" : booking.getDocumentLabel(); }

    public String getPassengerNic() {
        return booking != null ? booking.getPassengerNic() : "";
    }

    public String getOrigin() {
        return booking != null ? booking.getOrigin() : "";
    }

    public String getDestination() {
        return booking != null ? booking.getDestination() : "";
    }

    public String getDepartureTime() {
        return booking != null ? booking.getDepartureTime() : "";
    }

    public String getArrivalTime() {
        return booking != null ? booking.getArrivalTime() : "";
    }

    public LocalDate getTravelDate() {
        return booking != null ? booking.getTravelDate() : LocalDate.now();
    }

    public String getSeatClass() {
        return (booking != null && booking.getSeatClass() != null) ? booking.getSeatClass().name() : "FIRST";
    }

    public Integer getSeatCount() {
        return (booking != null && booking.getNumberOfSeats() != null) ? booking.getNumberOfSeats() : 1;
    }

    public String getSeatNumbers() {
        return (booking != null && booking.getSeatNumbers() != null) ? booking.getSeatNumbers() : "Car 01 / A1";
    }

    public BigDecimal getFarePaid() {
        if (payment != null && payment.getAmount() != null) {
            return payment.getAmount();
        }
        return booking != null ? booking.getTotalAmount() : BigDecimal.ZERO;
    }

    public BigDecimal getTotalAmount() {
        return getFarePaid();
    }

    public BigDecimal getTotalFare() {
        return getFarePaid();
    }
}
