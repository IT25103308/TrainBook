package com.trainbooking.it25102327.service;
import com.trainbooking.it25102327.model.*;
import java.time.*;
import java.util.*;

/** Validates recurring plans and dated exceptions, including next-day journeys and intermediate dwell times. */
public final class TimetableConflicts {
    private TimetableConflicts() { }
    private record Slot(String station,String platform,LocalDateTime start,LocalDateTime end) { }
    public static String find(Schedule candidate,List<Schedule> network) {
        if(!Boolean.TRUE.equals(candidate.getIsActive())) return null;
        Set<LocalDate> dates=new TreeSet<>();LocalDate monday=LocalDate.of(2000,1,3);
        for(int i=0;i<7;i++) dates.add(monday.plusDays(i));
        for(Schedule s:network) for(ScheduleOverride override:s.getOverrides()) if(override.getServiceDate()!=null && !override.getServiceDate().isBefore(LocalDate.now())) {
            dates.add(override.getServiceDate());dates.add(override.getServiceDate().minusDays(1));dates.add(override.getServiceDate().plusDays(1));
        }
        for(ScheduleOverride override:candidate.getOverrides()) if(override.getServiceDate()!=null) dates.add(override.getServiceDate());
        List<Schedule> others=new ArrayList<>(network.stream().filter(s->!Objects.equals(s.getId(),candidate.getId()) || candidate.getId()==null).toList());others.add(candidate);
        for(LocalDate date:dates) {
            ServicePlan a=ServicePlan.resolve(candidate,date);if(!operates(a)) continue;
            for(Schedule other:others) {
                if(!Boolean.TRUE.equals(other.getIsActive())) continue;
                for(int shift=-1;shift<=1;shift++) {
                    if(other==candidate && shift==0) continue;
                    ServicePlan b=ServicePlan.resolve(other,date.plusDays(shift));if(!operates(b)) continue;
                    if(a.schedule().getTrain()!=null && b.schedule().getTrain()!=null && Objects.equals(a.schedule().getTrain().getId(),b.schedule().getTrain().getId())
                        && overlaps(date.atTime(a.departure()),date.plusDays(a.arrivalDayOffset()).atTime(a.arrival()),b.date().atTime(b.departure()),b.date().plusDays(b.arrivalDayOffset()).atTime(b.arrival())))
                        return "Train conflict: this train already has an overlapping departure or needs the 20-minute turnaround allowance.";
                    for(Slot left:slots(a)) for(Slot right:slots(b)) if(left.station()!=null && right.station()!=null && left.station().equalsIgnoreCase(right.station())
                        && left.platform()!=null && left.platform().equalsIgnoreCase(right.platform()) && overlaps(left.start(),left.end(),right.start(),right.end()))
                        return "Platform conflict: "+left.station()+" / "+left.platform()+" overlaps another service within the 20-minute separation.";
                }
            }
        }
        return null;
    }
    private static boolean operates(ServicePlan plan) { return plan.runs() && !plan.maintenance() && plan.departure()!=null && plan.arrival()!=null; }
    private static boolean overlaps(LocalDateTime start,LocalDateTime end,LocalDateTime otherStart,LocalDateTime otherEnd) {
        return start.isBefore(otherEnd.plusMinutes(20)) && otherStart.isBefore(end.plusMinutes(20));
    }
    private static List<Slot> slots(ServicePlan p) {
        if(p.schedule().getRoute()==null) return List.of();List<Slot> slots=new ArrayList<>();
        LocalDateTime start=p.date().atTime(p.departure()),end=p.date().plusDays(p.arrivalDayOffset()).atTime(p.arrival());
        slots.add(new Slot(p.schedule().getRoute().getOrigin(),p.departurePlatform(),start,start));
        slots.add(new Slot(p.schedule().getRoute().getDestination(),p.arrivalPlatform(),end,end));
        for(TimetableStop stop:p.schedule().getStops()) slots.add(new Slot(stop.getStation(),stop.getPlatform(),start.plusMinutes(stop.getArrivalOffset()),start.plusMinutes(stop.getDepartureOffset())));
        return slots;
    }
}
