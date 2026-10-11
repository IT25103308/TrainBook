package com.trainbooking.it25102327.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;

/**
 * Data Transfer Object representing train search results for passenger booking queries.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainSearchResultDto {

    private Long scheduleId;
    private String trainName;
    private String trainNumber;
    private String origin;
    private String destination;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private Integer availableFirstClass;
    private Integer availableSecondClass;
    private BigDecimal firstClassFare;
    private BigDecimal secondClassFare;
    private String status;
    private BigDecimal displayFare;
    private String selectedClass;
    private Integer arrivalDayOffset;

    public boolean isFirstClassBookable() {
        return getAvailableFirstClassSeats() > 0 && firstClassFare != null && firstClassFare.signum() >= 0;
    }

    public boolean isSecondClassBookable() {
        return getAvailableSecondClassSeats() > 0 && secondClassFare != null && secondClassFare.signum() >= 0;
    }

    public boolean isSelectedClassAvailable() {
        return "FIRST".equals(selectedClass) ? isFirstClassBookable()
                : "SECOND".equals(selectedClass) ? isSecondClassBookable() : isFirstClassBookable() || isSecondClassBookable();
    }

    public String getSelectedClassLabel() {
        return "FIRST".equals(selectedClass) ? "1st Class (AC)" : "2nd Class Reserved";
    }

    public String getFormattedFirstClassFare() { return formatFare(firstClassFare); }
    public String getFormattedSecondClassFare() { return formatFare(secondClassFare); }

    private String formatFare(BigDecimal fare) {
        return fare == null ? "Unavailable" : "LKR " + String.format(java.util.Locale.ENGLISH, "%,.2f", fare);
    }

    public Long getId() {
        return scheduleId;
    }

    public Integer getAvailableFirstClassSeats() {
        return availableFirstClass != null ? availableFirstClass : 0;
    }

    public Integer getAvailableSecondClassSeats() {
        return availableSecondClass != null ? availableSecondClass : 0;
    }

    public String getDuration() {
        if (departureTime == null || arrivalTime == null) {
            return "N/A";
        }
        Duration duration = Duration.between(departureTime, arrivalTime);
        if (arrivalDayOffset != null) {
            duration = duration.plusDays(arrivalDayOffset);
        } else if (duration.isNegative()) {
            duration = duration.plusHours(24);
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return hours + "h " + minutes + "m";
    }

    public String getFormattedFare() {
        BigDecimal fare = displayFare != null ? displayFare : (secondClassFare != null ? secondClassFare : firstClassFare);
        if (fare == null) {
            return "LKR 0.00";
        }
        return "LKR " + String.format("%,.2f", fare);
    }
}
