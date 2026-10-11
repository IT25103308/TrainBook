package com.trainbooking.it25102327.dto;
import lombok.*;
import java.math.BigDecimal;
@Data @NoArgsConstructor
public class OverrideDto {
    private String serviceDate; private String kind="TIMETABLE"; private String label;
    private String departureTime; private String arrivalTime; private Integer arrivalDayOffset=0;
    private BigDecimal firstClassFare; private BigDecimal secondClassFare;
}
