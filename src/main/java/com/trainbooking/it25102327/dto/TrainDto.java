package com.trainbooking.it25102327.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for Train creation, update, and management.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainDto {

    private Long id;
    private String trainNumber;
    private String trainName;
    private String trainType;
    private Integer totalSeats;
    private Integer firstClassSeats;
    private Integer secondClassSeats;
    private String status;
}
