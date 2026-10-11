package com.trainbooking.it25100977.dto;

import java.time.LocalDateTime;

/** The owned checkout's last simulated outcome, with no card number or CVV storage. */
public record PaymentAttemptView(String status, String reference, LocalDateTime attemptedAt) {
    public String getLabel() {
        return switch (status) {
            case "FAILED" -> "Last payment attempt declined. No charge was made.";
            case "COMPLETED" -> "Simulated payment completed.";
            case "CANCELLED" -> "Unpaid payment request cancelled. No charge was made.";
            default -> "Payment has not completed.";
        };
    }
}
