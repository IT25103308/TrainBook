package com.trainbooking.it25102925.service;
import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25102327.service.*;
import com.trainbooking.it25102925.dto.*;
import com.trainbooking.it25102925.events.*;
import com.trainbooking.it25102925.model.ServiceDisruption;
import com.trainbooking.it25102925.repository.*;
import com.trainbooking.it25103308.repository.BookingRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ServiceStatusService {
    private final ScheduleRepository schedules;
    private final ServiceDisruptionRepository disruptions;
    private final BookingRepository bookings;
    private final CatalogWriteLock planningLock;
    private final ServiceChangeSubject subject;
    private final EntityManager entityManager;
    private final ServiceChangeAuditRepository audit;

    @Transactional(readOnly=true)
    public List<ServiceStatusRow> board(LocalDate date) {
        if(date==null) throw new IllegalArgumentException("Choose a service date.");
        return schedules.findAll().stream().map(s->ServicePlan.resolve(s,date)).filter(p->p.runs() && p.departure()!=null && p.schedule().getTrain()!=null && p.schedule().getRoute()!=null)
            .map(p->{var s=p.schedule();var d=p.disruption();return new ServiceStatusRow(s.getId(),s.getTrain().getId(),s.getTrain().getTrainName(),s.getTrain().getTrainNumber(),
                s.getRoute().getOrigin(),s.getRoute().getDestination(),date,p.departure(),p.arrival(),p.arrivalDayOffset(),p.maintenance()?"MAINTENANCE":p.status(),p.delayMinutes(),d==null?null:d.getReason(),d==null?null:d.getId());})
            .sorted(Comparator.comparing(ServiceStatusRow::departure).thenComparing(ServiceStatusRow::scheduleId)).toList();
    }
    @Transactional(readOnly=true)
    public List<com.trainbooking.it25102925.model.ServiceChangeAudit> recentChanges(){return audit.findTop30ByOrderByIdDesc();}

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void update(ServiceStatusRequest request,String actor) {
        if(request==null || request.getScheduleId()==null || request.getScheduleId()<1 || request.getServiceDate()==null) throw new IllegalArgumentException("Choose a service and its departure date.");
        ServiceDisruption.Status status;
        try {status=ServiceDisruption.Status.valueOf(request.getStatus()==null?"":request.getStatus().trim().toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Choose On time, Delayed or Cancelled.");}
        int delay=request.getDelayMinutes()==null?0:request.getDelayMinutes();
        if(status==ServiceDisruption.Status.DELAYED && (delay<1 || delay>1440)) throw new IllegalArgumentException("Enter a delay between 1 and 1440 minutes.");
        if(status!=ServiceDisruption.Status.DELAYED) delay=0;
        String reason=plainReason(request.getReason());
        planningLock.acquire();
        Schedule schedule=schedules.findByIdForUpdate(request.getScheduleId()).orElseThrow(()->new ResourceNotFoundException("Service not found."));
        refresh(schedule);ServicePlan plan=ServicePlan.resolve(schedule,request.getServiceDate());
        if(!plan.runs() || plan.departure()==null || plan.arrival()==null) throw new IllegalArgumentException("This service does not run on that date.");
        LocalDate date=request.getServiceDate();
        if(date.isBefore(LocalDate.now()) && (plan.arrivalDayOffset()!=1 || !date.plusDays(1).equals(LocalDate.now()) || date.plusDays(1).atTime(plan.arrival()).isBefore(LocalDateTime.now()))) {
            throw new IllegalArgumentException("Past completed departures cannot be changed. An active overnight departure may use yesterday's service date.");
        }
        var row=disruptions.findByScheduleIdAndServiceDate(schedule.getId(),date).orElseGet(()->ServiceDisruption.builder().schedule(schedule).serviceDate(date).build());
        String before=row.getStatus()==null ? plan.status() : row.getStatus().name();
        if(row.getStatus()==status && Objects.equals(row.getDelayMinutes(),delay) && Objects.equals(row.getReason(),reason)) return;
        row.setStatus(status);row.setDelayMinutes(delay);row.setReason(reason);row.setChangedBy(actor);row.setChangedAt(LocalDateTime.now());
        if(row.getId()==null) schedule.getDisruptions().add(row);
        disruptions.save(row);subject.publishAfterCommit(ServiceChange.of(schedule.getId(),date,before,status.name(),delay,reason,actor));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void deleteUnused(Long id,String actor) {
        var initial=disruptions.findById(id).orElseThrow(()->new ResourceNotFoundException("Service status record not found."));
        Long scheduleId=initial.getSchedule().getId();planningLock.acquire();
        var schedule=schedules.findByIdForUpdate(scheduleId).orElseThrow(()->new ResourceNotFoundException("Service not found."));refresh(schedule);
        var row=disruptions.findById(id).orElseThrow(()->new ResourceNotFoundException("Service status record not found."));
        if(row.getServiceDate().isBefore(LocalDate.now()) || bookings.findByScheduleId(scheduleId).stream().anyMatch(b->Objects.equals(b.getTravelDate(),row.getServiceDate()))) {
            throw new IllegalStateException("A past or booked status record is retained. Set On time to restore an eligible service.");
        }
        var event=ServiceChange.of(scheduleId,row.getServiceDate(),row.getStatus().name(),"ON_TIME",0,"Unused dated status removed",actor);
        schedule.getDisruptions().removeIf(d->d.getId().equals(id));disruptions.delete(row);subject.publishAfterCommit(event);
    }
    private void refresh(Schedule schedule){if(entityManager!=null){entityManager.refresh(schedule);if(schedule.getTrain()!=null)entityManager.refresh(schedule.getTrain());}}
    public static String plainReason(String value) {
        String text=value==null?"":value.strip();
        if(text.length()<3 || text.length()>300 || text.codePoints().anyMatch(c->Character.isISOControl(c)) || text.matches(".*[<>].*")) throw new IllegalArgumentException("Enter a plain-text reason of 3–300 characters, without markup.");
        return text;
    }
}
