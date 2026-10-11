package com.trainbooking.it25102925.dto;
import lombok.Data;
import java.time.*;
@Data
public class TelemetryForm {
    private Long scheduleId;
    @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) private LocalDate serviceDate;
    private Double latitude;
    private Double longitude;
    private Double speed;
    @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) private LocalDateTime expectedArrival;
    private boolean simulated;
}
