package com.trainbooking.it25102925.dto;
import java.time.*;
public record ServiceStatusRow(Long scheduleId,Long trainId,String trainName,String trainNumber,String origin,String destination,
                              LocalDate serviceDate,LocalTime departure,LocalTime arrival,int arrivalDayOffset,String status,int delayMinutes,String reason,Long disruptionId) {
    public String getDateIso(){return serviceDate.toString();}
    public String getJourney(){return trainName+" ("+trainNumber+") · "+origin+" → "+destination+" · "+departure;}
    public String getEstimatedDeparture(){return serviceDate.atTime(departure).plusMinutes(delayMinutes).toString();}
    public String getEstimatedArrival(){return arrival==null ? "Not recorded" : serviceDate.plusDays(arrivalDayOffset).atTime(arrival).plusMinutes(delayMinutes).toString();}
}
