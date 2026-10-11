package com.trainbooking.it25102925.dto;
import java.time.*;
public record RebookingOption(Long scheduleId,LocalDate date,String trainName,String trainNumber,String origin,String destination,
                             LocalTime departure,LocalTime arrival,int arrivalDayOffset,int availableSeats,String seatClass) {
    public String getDateIso(){return date.toString();}
}
