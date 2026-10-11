package com.trainbooking.it25102925.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object representing a train's real-time GPS coordinates,
 * operational metrics, and route waypoint information.
 *
 * @author SLIIT Software Engineering Team (IT25102925)
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainLocationDto {

    private Long trainId;
    private String trainNumber;
    private String trainName;
    private String route;
    private Double latitude;
    private Double longitude;
    private Double speed;
    private String heading;
    private String status;
    private String currentStation;
    private String nextStation;
    private String tripDirection; // "OUTBOUND" or "RETURN"
    private Integer progressPercentage; // 0 to 100
    private LocalDateTime updatedAt;
    private Long scheduleId;
    private java.time.LocalDate serviceDate;
    private Boolean simulated;

    public String getFormattedSpeed() {
        if (speed == null) return "0.0 km/h";
        return String.format("%.1f km/h", speed);
    }
}
