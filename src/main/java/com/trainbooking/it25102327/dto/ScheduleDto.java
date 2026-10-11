package com.trainbooking.it25102327.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Data Transfer Object representing train schedule and fare information.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDto {

    private Long id;
    private Long trainId;
    private String trainName;
    private String trainNumber;
    private Long routeId;
    private String origin;
    private String destination;
    private String departureTime;
    private String arrivalTime;
    private String dayOfWeek;
    private BigDecimal firstClassFare;
    private BigDecimal secondClassFare;
    private Boolean isActive;
    private String defaultPlatform;
    private Boolean isSeasonal;
    private String seasonalName;
    private Boolean isMaintenanceBlocked;
    private String maintenanceNotes;
    private Integer arrivalDayOffset;
    private String farePolicy;
    private String departurePlatform;
    private String arrivalPlatform;
    @Builder.Default private java.util.List<StopDto> stops=new java.util.ArrayList<>();
}
