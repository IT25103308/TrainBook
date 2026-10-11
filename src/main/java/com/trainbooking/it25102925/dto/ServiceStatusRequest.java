package com.trainbooking.it25102925.dto;
import lombok.Data;
import java.time.LocalDate;
@Data
public class ServiceStatusRequest {
    private Long scheduleId;
    @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private LocalDate serviceDate;
    private String status;
    private Integer delayMinutes;
    private String reason;
}
