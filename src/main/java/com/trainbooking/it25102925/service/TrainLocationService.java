package com.trainbooking.it25102925.service;
import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25102327.repository.*;
import com.trainbooking.it25102327.service.ServicePlan;
import com.trainbooking.it25102925.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Reported/simulated telemetry, never invented baseline train positions. */
@Service @RequiredArgsConstructor
public class TrainLocationService {
    private final TrainRepository trainRepository;
    private final RouteRepository routeRepository;
    private final NotificationService notificationService;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private ScheduleRepository schedules;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private ServiceStatusService statuses;
    private final ConcurrentMap<Long,TrainLocationDto> liveLocations=new ConcurrentHashMap<>();
    @Transactional public TrainLocationDto updateLocation(Long trainId,Double latitude,Double longitude,Double speed,String status,String station,String next){return updateLocation(trainId,latitude,longitude,speed,status,station,next,null,null,null);}
    @Transactional public TrainLocationDto updateLocation(Long trainId,Double latitude,Double longitude,Double speed,String status,String station,String next,String direction,String route,Integer progress){
        return report(trainId,null,null,latitude,longitude,speed,status,station,next,direction,route,progress,null,null,false,"position reporter");
    }
    @Transactional public TrainLocationDto report(Long trainId,Long scheduleId,LocalDate date,Double latitude,Double longitude,Double speed,String status,
            String station,String next,String direction,String customRoute,Integer progress,Integer delay,LocalDateTime expectedArrival,boolean simulated,String actor) {
        if(trainId==null || trainId<1 || latitude==null || longitude==null || !Double.isFinite(latitude) || !Double.isFinite(longitude) || latitude<-90 || latitude>90 || longitude<-180 || longitude>180) throw new IllegalArgumentException("Choose a train and finite latitude (-90 to 90) / longitude (-180 to 180).");
        if(speed!=null && (!Double.isFinite(speed) || speed<0 || speed>200)) throw new IllegalArgumentException("Speed must be a finite value from 0 to 200 km/h.");
        if(progress!=null && (progress<0 || progress>100))throw new IllegalArgumentException("Progress must be a whole percentage from 0 to 100.");
        String dir=direction==null || direction.isBlank()?"OUTBOUND":direction.trim().toUpperCase(Locale.ROOT);
        if(!Set.of("OUTBOUND","RETURN","AT_TERMINAL").contains(dir))throw new IllegalArgumentException("Choose OUTBOUND, RETURN or AT_TERMINAL direction.");
        String state=status==null || status.isBlank()?null:status.trim().toUpperCase(Locale.ROOT);
        if(state!=null && !Set.of("ON_TIME","DELAYED","CANCELLED","AT_TERMINAL").contains(state))throw new IllegalArgumentException("Choose a supported telemetry status.");
        var train=trainRepository.findById(trainId).orElseThrow(()->new ResourceNotFoundException("Train not found."));
        String route=routeRepository.findAll().stream().filter(r->r.getTrain()!=null && Objects.equals(r.getTrain().getId(),trainId)).map(r->r.getOrigin()+" → "+r.getDestination()).findFirst().orElse("Route not assigned");
        ServicePlan plan=null;
        if(scheduleId!=null) {
            if(schedules==null || statuses==null || date==null)throw new IllegalArgumentException("Choose the actual service and departure date.");
            var schedule=schedules.findById(scheduleId).orElseThrow(()->new ResourceNotFoundException("Service not found."));
            if(schedule.getTrain()==null || !Objects.equals(schedule.getTrain().getId(),trainId))throw new IllegalArgumentException("The service does not belong to this train.");
            plan=ServicePlan.resolve(schedule,date);
            if(!plan.runs() || plan.arrival()==null || !date.equals(LocalDate.now()) && !(date.equals(LocalDate.now().minusDays(1)) && plan.arrivalDayOffset()==1 && date.plusDays(1).atTime(plan.arrival()).isAfter(LocalDateTime.now())))throw new IllegalArgumentException("Telemetry must refer to today's departure or an active overnight service.");
            route=schedule.getRoute().getOrigin()+" → "+schedule.getRoute().getDestination();
            if(expectedArrival!=null){var planned=date.plusDays(plan.arrivalDayOffset()).atTime(plan.arrival());if(expectedArrival.isBefore(planned) || expectedArrival.isAfter(planned.plusHours(24)))throw new IllegalArgumentException("Expected arrival must be between planned arrival and 24 hours later.");long minutes=java.time.Duration.between(planned,expectedArrival).toMinutes();delay=(int)minutes;state=delay>0?"DELAYED":"ON_TIME";}
            if(plan.isCancelled() && state!=null && !Set.of("CANCELLED","AT_TERMINAL").contains(state))throw new IllegalStateException("Restore a cancelled departure explicitly before telemetry can change its status.");
        } else if(expectedArrival!=null || state!=null && Set.of("DELAYED","CANCELLED").contains(state)) throw new IllegalArgumentException("A delay or cancellation needs an explicit service and departure date.");
        if(customRoute!=null && !customRoute.isBlank() && !customRoute.trim().equals(route))throw new IllegalArgumentException("Use the configured route; telemetry cannot replace it.");
        String current=label(station),nextStation=label(next);
        if(plan!=null){var allowed=new HashSet<String>();allowed.add(plan.schedule().getRoute().getOrigin());allowed.add(plan.schedule().getRoute().getDestination());for(var stop:plan.stops())allowed.add(stop.station());
            if(!current.isEmpty() && !allowed.contains(current) || !nextStation.isEmpty() && !allowed.contains(nextStation))throw new IllegalArgumentException("Choose a configured route endpoint/stop, or leave the station blank.");}
        if(plan!=null && state!=null && !state.equals("AT_TERMINAL")){var request=new ServiceStatusRequest();request.setScheduleId(scheduleId);request.setServiceDate(date);request.setStatus(state);request.setDelayMinutes(delay);request.setReason(simulated?"Simulated telemetry deviation":"Reported service telemetry");statuses.update(request,actor);}
        String effective=state==null ? plan!=null?plan.status():train.getStatus()==null?"UNAVAILABLE":train.getStatus().name():state;
        var dto=TrainLocationDto.builder().trainId(trainId).trainNumber(train.getTrainNumber()).trainName(train.getTrainName()).route(route).latitude(latitude).longitude(longitude)
            .speed(speed==null?0:speed).status(effective).currentStation(current.isEmpty()?"In transit":current).nextStation(nextStation).tripDirection(dir).progressPercentage(progress).updatedAt(LocalDateTime.now())
            .scheduleId(scheduleId).serviceDate(date).simulated(simulated).build();
        commit(()->liveLocations.put(trainId,dto));return dto;
    }
    @Transactional public void clear(Long trainId){if(trainId==null || !trainRepository.existsById(trainId))throw new ResourceNotFoundException("Train not found.");commit(()->liveLocations.remove(trainId));}
    public Optional<TrainLocationDto> getLocation(Long trainId){if(trainId==null)return Optional.empty();return Optional.ofNullable(liveLocations.get(trainId));}
    public List<TrainLocationDto> getAllLocations(){var ids=trainRepository.findAll().stream().map(t->t.getId()).collect(java.util.stream.Collectors.toSet());liveLocations.keySet().removeIf(id->!ids.contains(id));return liveLocations.values().stream().sorted(Comparator.comparing(TrainLocationDto::getTrainId)).toList();}
    private String label(String value){String text=value==null?"":value.strip();if(text.length()>100 || text.matches(".*[<>].*") || text.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Use a plain station label of up to 100 characters.");return text;}
    private void commit(Runnable action){if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){action.run();}});else action.run();}
}
