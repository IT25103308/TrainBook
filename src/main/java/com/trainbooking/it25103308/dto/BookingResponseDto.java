package com.trainbooking.it25103308.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Data Transfer Object representing the response details of a train booking.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponseDto {

    private Long id;
    private String trainName;
    private String origin;
    private String destination;
    private LocalDate travelDate;
    private String seatClass;
    private Integer numberOfSeats;
    private String status;
    private String ticketNumber;
}
