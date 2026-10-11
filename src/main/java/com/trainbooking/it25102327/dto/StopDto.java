package com.trainbooking.it25102327.dto;
import lombok.*;
@Data @NoArgsConstructor @AllArgsConstructor
public class StopDto { private String station; private String arrivalTime; private String departureTime; private Integer dayOffset=0; private String platform; }
