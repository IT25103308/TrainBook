package com.trainbooking.it25102327.service;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.fare.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Single resolution rule reused by public search, reservation, checkout and dated staff previews. */
public record ServicePlan(Schedule schedule, LocalDate date, boolean runs, boolean maintenance,
                          LocalTime departure, LocalTime arrival, int arrivalDayOffset,
                          BigDecimal firstFare, BigDecimal secondFare, String label) {
    private static final FareCalculator FARES=FareCalculator.standard();
    public static ServicePlan resolve(Schedule s,LocalDate date) {
        var override=s.getOverrides().stream().filter(o->Objects.equals(o.getServiceDate(),date) && o.getKind()==ScheduleOverride.Kind.TIMETABLE).findFirst().orElse(null);
        var maintenance=s.getOverrides().stream().filter(o->Objects.equals(o.getServiceDate(),date) && o.getKind()==ScheduleOverride.Kind.MAINTENANCE).findFirst().orElse(null);
        boolean special=override!=null && override.getKind()==ScheduleOverride.Kind.TIMETABLE;
        LocalTime departure=special ? override.getDepartureTime() : s.getDepartureTime();
        LocalTime arrival=special ? override.getArrivalTime() : s.getArrivalTime();
        Integer offset=special ? override.getArrivalDayOffset() : s.getArrivalDayOffset();
        if(offset==null) offset=departure!=null && arrival!=null && arrival.isBefore(departure) ? 1 : 0;
        return new ServicePlan(s,date,date!=null && (special || s.getDayOfWeek()==date.getDayOfWeek()),
            Boolean.TRUE.equals(s.getIsMaintenanceBlocked()) || maintenance!=null,
            departure,arrival,offset,special && override.getFirstClassFare()!=null ? override.getFirstClassFare() : s.getFirstClassFare(),
            special && override.getSecondClassFare()!=null ? override.getSecondClassFare() : s.getSecondClassFare(),maintenance!=null ? maintenance.getLabel() : override==null ? null : override.getLabel());
    }
    public boolean availableAt(LocalDateTime now) {
        Train train=schedule.getTrain();
        return runs && !maintenance && !isCancelled() && Boolean.TRUE.equals(schedule.getIsActive()) && train!=null && train.getStatus()!=null
            && !Set.of(Train.TrainStatus.CANCELLED,Train.TrainStatus.MAINTENANCE,Train.TrainStatus.INACTIVE).contains(train.getStatus())
            && departure!=null && date.atTime(departure).isAfter(now);
    }
    public com.trainbooking.it25102925.model.ServiceDisruption disruption() {
        return schedule.getDisruptions().stream().filter(d->Objects.equals(d.getServiceDate(),date)).findFirst().orElse(null);
    }
    public boolean isCancelled() {
        return disruption()!=null && disruption().getStatus()==com.trainbooking.it25102925.model.ServiceDisruption.Status.CANCELLED
            || schedule.getTrain()!=null && schedule.getTrain().getStatus()==Train.TrainStatus.CANCELLED;
    }
    public int delayMinutes() { return disruption()==null || disruption().getDelayMinutes()==null ? 0 : disruption().getDelayMinutes(); }
    public String status() {
        if(schedule.getTrain()!=null && schedule.getTrain().getStatus()!=null && Set.of(Train.TrainStatus.CANCELLED,Train.TrainStatus.MAINTENANCE,Train.TrainStatus.INACTIVE).contains(schedule.getTrain().getStatus())) return schedule.getTrain().getStatus().name();
        if(disruption()!=null) return disruption().getStatus().name();
        if(date!=null && !date.equals(LocalDate.now())) return "SCHEDULED";
        return schedule.getTrain()==null || schedule.getTrain().getStatus()==null ? "UNAVAILABLE"
            : schedule.getTrain().getStatus()==Train.TrainStatus.ACTIVE ? "ON_TIME" : schedule.getTrain().getStatus().name();
    }
    public BigDecimal fare(String seatClass) {
        return FARES.quote(new FareContext(schedule.getFarePolicy(),seatClass,schedule.getRoute()==null ? null : schedule.getRoute().getDistanceKm(),
            departure,date==null ? null : date.getDayOfWeek(),seatClass.equals("FIRST") ? firstFare : secondFare));
    }
    public String departurePlatform() { return schedule.getDeparturePlatform()!=null ? schedule.getDeparturePlatform() : schedule.getRoute()==null ? null : schedule.getRoute().getDefaultPlatform(); }
    public String arrivalPlatform() { return schedule.getArrivalPlatform()!=null ? schedule.getArrivalPlatform() : schedule.getRoute()==null ? null : schedule.getRoute().getDestinationPlatform(); }
    public List<StopView> stops() {
        if(departure==null) return List.of();
        return schedule.getStops().stream().map(s->new StopView(s.getStation(),departure.plusMinutes(s.getArrivalOffset()),
            departure.plusMinutes(s.getDepartureOffset()),(departure.toSecondOfDay()/60+s.getArrivalOffset())/1440,(departure.toSecondOfDay()/60+s.getDepartureOffset())/1440,s.getPlatform())).toList();
    }
    public record StopView(String station,LocalTime arrival,LocalTime departure,int dayOffset,int departureDayOffset,String platform) { }
}
