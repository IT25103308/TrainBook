package com.trainbooking.it25102327.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing a train route between two stations.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteDto {

    private Long id;
    private String origin;
    private String destination;
    private Integer distanceKm;
    private Long trainId;
    private String defaultPlatform;
    private String destinationPlatform;
}
