package com.trainbooking.it25100228.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Data Transfer Object representing ticket QR code validation results.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResultDto {

    private Boolean valid;
    private String message;
    private String ticketNumber;
    private String passengerName;
    private String trainName;
    private String origin;
    private String destination;
    private String seatClass;
    private String seatNumbers;
    private LocalDate travelDate;
}
