package com.trainbooking.it25102925.events;
import java.time.*;
import java.util.UUID;

/** Small immutable event contract shared by the subject and application observers. */
public record ServiceChange(String eventId,Long scheduleId,LocalDate serviceDate,String previousStatus,
                            String status,int delayMinutes,String reason,String actor,LocalDateTime changedAt) {
    public static ServiceChange of(Long scheduleId,LocalDate date,String before,String status,int delay,String reason,String actor) {
        return new ServiceChange(UUID.randomUUID().toString(),scheduleId,date,before,status,delay,reason,actor,LocalDateTime.now());
    }
}
