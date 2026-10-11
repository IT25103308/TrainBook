package com.trainbooking.it25103308.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Data Transfer Object representing a passenger train reservation request.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    private Long scheduleId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate travelDate;

    private String seatClass;

    private Integer numberOfSeats;

    private Integer seatCount;

    private String seatNumbers;

    private String passengerName;

    private String documentType;

    private String passengerNic;

    private String contactPhone;

    public BookingRequest(Long scheduleId, LocalDate travelDate, String seatClass, Integer numberOfSeats) {
        this.scheduleId = scheduleId;
        this.travelDate = travelDate;
        this.seatClass = seatClass;
        this.numberOfSeats = numberOfSeats;
        this.seatCount = numberOfSeats;
    }

    public Integer getNumberOfSeats() {
        if (numberOfSeats != null && seatCount != null && !numberOfSeats.equals(seatCount)) {
            throw new IllegalArgumentException("Seat quantities do not match. Please select your seats again.");
        }
        return numberOfSeats != null ? numberOfSeats : seatCount;
    }
}
