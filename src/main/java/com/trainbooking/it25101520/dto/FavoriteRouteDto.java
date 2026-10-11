package com.trainbooking.it25101520.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for passenger favorite train routes.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteRouteDto {

    private Long id;
    @jakarta.validation.constraints.NotBlank(message = "Choose an origin station")
    @jakarta.validation.constraints.Size(max = 100)
    private String origin;
    @jakarta.validation.constraints.NotBlank(message = "Choose a destination station")
    @jakarta.validation.constraints.Size(max = 100)
    private String destination;
}
