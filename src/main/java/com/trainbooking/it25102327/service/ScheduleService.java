package com.trainbooking.it25102327.service;

import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25102327.dto.*;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.repository.*;
import com.trainbooking.it25102327.fare.*;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Operational edits preserve reservation/payment history and publish one coherent plan. */
@Service @RequiredArgsConstructor
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;
    private final TrainRepository trainRepository;
    private final RouteRepository routeRepository;
    private final BookingRepository bookingRepository;
    private final CatalogWriteLock catalogWriteLock;

    @Transactional(readOnly=true) public List<ScheduleDto> getAllSchedules() {
        return scheduleRepository.findAll().stream().sorted(Comparator.comparing(Schedule::getId).reversed()).map(this::mapToDto).toList();
    }
    @Transactional(readOnly=true) public Schedule getScheduleById(Long id) {
        return scheduleRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Schedule not found."));
    }
    @Transactional(readOnly=true) public ScheduleDto getScheduleDto(Long id) { return mapToDto(getScheduleById(id)); }
    @Transactional(readOnly=true) public Map<String,Object> quote(Long id,LocalDate date,String seatClass) {
        Schedule s=getScheduleById(id);ServicePlan plan=ServicePlan.resolve(s,date);
        if(plan.departure()==null || plan.arrival()==null) throw new IllegalArgumentException("Configure this timetable's departure and arrival before requesting a quote.");
        return Map.of("perSeatFare",plan.fare(seatClass),"farePolicy",policy(s),"runs",plan.availableAt(LocalDateTime.now()),"departure",plan.departure().toString(),"arrival",plan.arrival().toString());
    }
    public void lockCatalog() { catalogWriteLock.acquire(); }
    private Schedule locked(Long id) { return scheduleRepository.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Schedule not found.")); }
    public List<Schedule> lockTrainInventory(Long trainId) {
        return scheduleRepository.findByTrainId(trainId).stream().sorted(Comparator.comparing(Schedule::getId)).map(s->locked(s.getId())).toList();
    }
    public void guardTrainEdit(Train train,TrainDto dto) {
        List<Schedule> schedules=lockTrainInventory(train.getId());
        boolean identityChanged=!Objects.equals(train.getTrainNumber(),dto.getTrainNumber()) || !Objects.equals(train.getTrainName(),dto.getTrainName());
        for(Schedule schedule:schedules) for(Booking b:bookingRepository.findByScheduleId(schedule.getId())) {
            if(identityChanged) OperationalRules.fail("trainNumber","This train has booking history. Keep its number/name and create another train for a different identity.");
            if(b.getTravelDate()==null || b.getTravelDate().isBefore(LocalDate.now()) || b.getStatus()==Booking.BookingStatus.CANCELLED) continue;
            int capacity=b.getSeatClass()==Booking.SeatClass.FIRST ? dto.getFirstClassSeats() : dto.getSecondClassSeats();
            String field=b.getSeatClass()==Booking.SeatClass.FIRST ? "firstClassSeats" : "secondClassSeats";
            try {
                var allocated=com.trainbooking.it25103308.service.SeatSelection.parseStored(b.getSeatNumbers(),b.getSeatClass(),capacity);
                if(b.getNumberOfSeats()==null || allocated.size()!=b.getNumberOfSeats()) throw new IllegalArgumentException();
            } catch(IllegalArgumentException invalid) { OperationalRules.fail(field,"The proposed capacity would remove allocated seats or affect records needing review. Keep the current capacity."); }
        }
    }
    @Transactional(readOnly=true) public List<Schedule> getSchedulesByTrainId(Long id) { return scheduleRepository.findByTrainId(id); }
    @Transactional(readOnly=true) public List<Route> getAllRoutes() { return routeRepository.findAll().stream().sorted(Comparator.comparing(Route::getId).reversed()).toList(); }
    @Transactional(readOnly=true) public Route getRouteById(Long id) { return routeRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Route not found.")); }
    public RouteDto getRouteDto(Long id) {
        Route r=getRouteById(id);return RouteDto.builder().id(id).origin(r.getOrigin()).destination(r.getDestination()).distanceKm(r.getDistanceKm())
            .defaultPlatform(r.getDefaultPlatform()).destinationPlatform(r.getDestinationPlatform()==null ? "Platform 1" : r.getDestinationPlatform()).build();
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Route createRoute(String origin,String destination,Integer distance,String platform) {
        return createRoute(RouteDto.builder().origin(origin).destination(destination).distanceKm(distance).defaultPlatform(platform).destinationPlatform("Platform 1").build());
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Route createRoute(RouteDto dto) {
        lockCatalog();OperationalRules.route(dto);normaliseStations(dto);duplicateRoute(dto,null);
        return routeRepository.save(Route.builder().origin(dto.getOrigin()).destination(dto.getDestination()).distanceKm(dto.getDistanceKm())
            .defaultPlatform(dto.getDefaultPlatform()).destinationPlatform(dto.getDestinationPlatform()).build());
    }
    private void normaliseStations(RouteDto dto) {
        for(Route r:routeRepository.findAll()) for(String station:java.util.stream.Stream.of(r.getOrigin(),r.getDestination()).filter(Objects::nonNull).toList()) {
            if(station.equalsIgnoreCase(dto.getOrigin())) dto.setOrigin(station);
            if(station.equalsIgnoreCase(dto.getDestination())) dto.setDestination(station);
        }
    }
    private void duplicateRoute(RouteDto dto,Long ownId) {
        if(routeRepository.findAll().stream().anyMatch(r->!Objects.equals(r.getId(),ownId) && r.getOrigin().equalsIgnoreCase(dto.getOrigin()) && r.getDestination().equalsIgnoreCase(dto.getDestination())))
            OperationalRules.fail("destination","This origin/destination route already exists. Edit the existing route instead.");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public Route updateRoute(Long id,RouteDto dto) {
        lockCatalog();Route original=getRouteById(id);OperationalRules.route(dto);normaliseStations(dto);duplicateRoute(dto,id);
        List<Schedule> affected=scheduleRepository.findAll().stream().filter(s->s.getRoute()!=null && Objects.equals(s.getRoute().getId(),id))
            .sorted(Comparator.comparing(Schedule::getId)).map(s->locked(s.getId())).toList();
        boolean changed=!Objects.equals(original.getOrigin(),dto.getOrigin()) || !Objects.equals(original.getDestination(),dto.getDestination())
            || !Objects.equals(original.getDistanceKm(),dto.getDistanceKm()) || !Objects.equals(original.getDefaultPlatform(),dto.getDefaultPlatform())
            || !Objects.equals(original.getDestinationPlatform()==null ? "Platform 1" : original.getDestinationPlatform(),dto.getDestinationPlatform());
        if(changed && affected.stream().anyMatch(s->!bookingRepository.findByScheduleId(s.getId()).isEmpty()))
            throw new IllegalArgumentException("This route has booking history. Keep its saved details and create a separate route for a new corridor.");
        Route replacement=Route.builder().id(id).origin(dto.getOrigin()).destination(dto.getDestination()).distanceKm(dto.getDistanceKm())
            .defaultPlatform(dto.getDefaultPlatform()).destinationPlatform(dto.getDestinationPlatform()).build();
        List<Schedule> network=scheduleRepository.findAll().stream().map(s->Objects.equals(s.getRoute()==null ? null : s.getRoute().getId(),id) ? copyWithRoute(s,replacement) : s).toList();
        for(Schedule s:network) if(s.getRoute()!=null && Objects.equals(s.getRoute().getId(),id)) checkConflict(s,network);
        original.setOrigin(dto.getOrigin());original.setDestination(dto.getDestination());original.setDistanceKm(dto.getDistanceKm());
        original.setDefaultPlatform(dto.getDefaultPlatform());original.setDestinationPlatform(dto.getDestinationPlatform());return routeRepository.save(original);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void deleteRoute(Long id) {
        lockCatalog();Route route=getRouteById(id);
        if(scheduleRepository.findAll().stream().anyMatch(s->s.getRoute()!=null && Objects.equals(s.getRoute().getId(),id)))
            throw new IllegalArgumentException("This route is used by timetables. Delete only unused timetables, or retain/deactivate the service.");
        routeRepository.delete(route);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public ScheduleDto createSchedule(ScheduleDto dto) {
        lockCatalog();Schedule s=candidate(dto,null);checkConflict(s,scheduleRepository.findAll());return mapToDto(scheduleRepository.save(s));
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public ScheduleDto updateSchedule(Long id,ScheduleDto dto) {
        lockCatalog();Schedule original=locked(id);Schedule next=candidate(dto,original);next.setId(id);next.setOverrides(original.getOverrides());
        if(structuralChange(original,next) && !bookingRepository.findByScheduleId(id).isEmpty())
            throw new IllegalArgumentException("This timetable has booking history. Keep its journey details; use a dated override for an unbooked departure or create a new timetable.");
        checkConflict(next,scheduleRepository.findAll());
        original.setTrain(next.getTrain());original.setRoute(next.getRoute());original.setDepartureTime(next.getDepartureTime());original.setArrivalTime(next.getArrivalTime());
        original.setArrivalDayOffset(next.getArrivalDayOffset());original.setDayOfWeek(next.getDayOfWeek());original.setFirstClassFare(next.getFirstClassFare());original.setSecondClassFare(next.getSecondClassFare());
        original.setFarePolicy(next.getFarePolicy());original.setDeparturePlatform(next.getDeparturePlatform());original.setArrivalPlatform(next.getArrivalPlatform());original.setIsActive(next.getIsActive());
        if(!sameStops(original.getStops(),next.getStops())) { original.getStops().clear();for(TimetableStop stop:next.getStops()) { stop.setSchedule(original);original.getStops().add(stop); } }
        return mapToDto(scheduleRepository.save(original));
    }
    private Schedule candidate(ScheduleDto dto,Schedule old) {
        Long trainId=dto.getTrainId()!=null ? dto.getTrainId() : old==null ? null : old.getTrain().getId();
        Long routeId=dto.getRouteId()!=null ? dto.getRouteId() : old==null ? null : old.getRoute().getId();
        if(trainId==null || trainId<1) OperationalRules.fail("trainId","Choose a registered train.");
        if(routeId==null || routeId<1) OperationalRules.fail("routeId","Choose a configured route.");
        Train train=trainRepository.findById(trainId).orElseThrow(()->new OperationValidation("trainId","Choose an existing train."));
        if((train.getFirstClassSeats()==null ? 0 : train.getFirstClassSeats())+(train.getSecondClassSeats()==null ? 0 : train.getSecondClassSeats())<1)
            OperationalRules.fail("trainId","Configure at least one supported class capacity for this train first.");
        Route route=routeRepository.findById(routeId).orElseThrow(()->new OperationValidation("routeId","Choose an existing route."));
        LocalTime departure=OperationalRules.time(dto.getDepartureTime(),"departureTime"),arrival=OperationalRules.time(dto.getArrivalTime(),"arrivalTime");
        Integer offset=dto.getArrivalDayOffset()!=null ? dto.getArrivalDayOffset() : old==null ? 0 : offset(old);
        OperationalRules.duration(departure,arrival,offset,"arrivalTime");
        DayOfWeek day;try { day=DayOfWeek.valueOf(dto.getDayOfWeek()==null ? "" : dto.getDayOfWeek().toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException invalid) { throw new OperationValidation("dayOfWeek","Choose a valid operating weekday."); }
        String policy=dto.getFarePolicy()==null ? old==null ? "FIXED" : policy(old) : dto.getFarePolicy();
        if(!Set.of("FIXED","DEMAND").contains(policy)) OperationalRules.fail("farePolicy","Choose a supported fare policy.");
        BigDecimal first=dto.getFirstClassFare()!=null ? dto.getFirstClassFare() : old==null ? null : old.getFirstClassFare();
        BigDecimal second=dto.getSecondClassFare()!=null ? dto.getSecondClassFare() : old==null ? null : old.getSecondClassFare();
        first=OperationalRules.fare(first,"firstClassFare",policy.equals("FIXED") && train.getFirstClassSeats()!=null && train.getFirstClassSeats()>0);
        second=OperationalRules.fare(second,"secondClassFare",policy.equals("FIXED") && train.getSecondClassSeats()!=null && train.getSecondClassSeats()>0);
        Schedule s=Schedule.builder().train(train).route(route).departureTime(departure).arrivalTime(arrival).arrivalDayOffset(offset).dayOfWeek(day)
            .firstClassFare(first).secondClassFare(second).farePolicy(policy).isActive(dto.getIsActive()==null ? old==null || Boolean.TRUE.equals(old.getIsActive()) : dto.getIsActive())
            .departurePlatform(OperationalRules.platform(dto.getDeparturePlatform()==null || dto.getDeparturePlatform().isBlank() ? route.getDefaultPlatform()==null ? "Platform 1" : route.getDefaultPlatform() : dto.getDeparturePlatform(),"departurePlatform"))
            .arrivalPlatform(OperationalRules.platform(dto.getArrivalPlatform()==null || dto.getArrivalPlatform().isBlank() ? route.getDestinationPlatform()==null ? "Platform 1" : route.getDestinationPlatform() : dto.getArrivalPlatform(),"arrivalPlatform"))
            .isMaintenanceBlocked(old!=null && Boolean.TRUE.equals(old.getIsMaintenanceBlocked())).maintenanceNotes(old==null ? null : old.getMaintenanceNotes()).build();
        s.setStops(OperationalRules.stops(dto.getStops(),s));return s;
    }
    private int offset(Schedule s) { return s.getArrivalDayOffset()!=null ? s.getArrivalDayOffset() : s.getArrivalTime()!=null && s.getDepartureTime()!=null && s.getArrivalTime().isBefore(s.getDepartureTime()) ? 1 : 0; }
    private String policy(Schedule s) { return s.getFarePolicy()==null ? "FIXED" : s.getFarePolicy(); }
    private boolean structuralChange(Schedule a,Schedule b) {
        return !Objects.equals(a.getTrain().getId(),b.getTrain().getId()) || !Objects.equals(a.getRoute().getId(),b.getRoute().getId())
            || !Objects.equals(a.getDepartureTime(),b.getDepartureTime()) || !Objects.equals(a.getArrivalTime(),b.getArrivalTime()) || offset(a)!=offset(b)
            || a.getDayOfWeek()!=b.getDayOfWeek() || !Objects.equals(ServicePlan.resolve(a,LocalDate.now()).departurePlatform(),b.getDeparturePlatform())
            || !Objects.equals(ServicePlan.resolve(a,LocalDate.now()).arrivalPlatform()==null ? "Platform 1" : ServicePlan.resolve(a,LocalDate.now()).arrivalPlatform(),b.getArrivalPlatform())
            || !sameStops(a.getStops(),b.getStops());
    }
    private boolean sameStops(List<TimetableStop> a,List<TimetableStop> b) {
        if(a.size()!=b.size()) return false;for(int i=0;i<a.size();i++) if(!Objects.equals(a.get(i).getStation(),b.get(i).getStation()) || a.get(i).getArrivalOffset()!=b.get(i).getArrivalOffset()
            || a.get(i).getDepartureOffset()!=b.get(i).getDepartureOffset() || !Objects.equals(a.get(i).getPlatform(),b.get(i).getPlatform())) return false;return true;
    }
    private Schedule copyWithRoute(Schedule s,Route route) {
        Schedule copy=Schedule.builder().id(s.getId()).train(s.getTrain()).route(route).departureTime(s.getDepartureTime()).arrivalTime(s.getArrivalTime()).arrivalDayOffset(s.getArrivalDayOffset())
            .dayOfWeek(s.getDayOfWeek()).isActive(s.getIsActive()).isMaintenanceBlocked(s.getIsMaintenanceBlocked()).departurePlatform(s.getDeparturePlatform()).arrivalPlatform(s.getArrivalPlatform()).build();
        copy.setStops(s.getStops());copy.setOverrides(s.getOverrides());return copy;
    }
    private void checkConflict(Schedule s,List<Schedule> network) { String message=TimetableConflicts.find(s,network);if(message!=null) throw new OperationValidation("departureTime",message); }
    public String validatePlatformConflict(Schedule s) { return TimetableConflicts.find(s,scheduleRepository.findAll()); }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void deleteSchedule(Long id) {
        lockCatalog();Schedule s=locked(id);if(!bookingRepository.findByScheduleId(id).isEmpty()) throw new IllegalArgumentException("This timetable has booking history and cannot be deleted. Deactivate it to stop new reservations.");
        scheduleRepository.delete(s);
    }
    public BigDecimal calculateDynamicFare(Integer distance,String seatClass,LocalTime departure,DayOfWeek day) {
        return FareCalculator.standard().quote(new FareContext("DEMAND",seatClass,distance,departure,day,null));
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public ScheduleDto setSeasonalOverride(Long id,boolean enabled,String name) {
        if(enabled) throw new IllegalArgumentException("Choose a date in Dated service changes; a seasonal label alone does not override the timetable.");
        lockCatalog();Schedule s=locked(id);s.setIsSeasonal(false);s.setSeasonalName(null);return mapToDto(scheduleRepository.save(s));
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public String setMaintenanceBlock(Long id,boolean blocked,String notes) {
        lockCatalog();Schedule s=locked(id);String clean=blocked ? OperationalRules.note(notes,"notes",300) : null;
        if(!blocked) { Schedule candidate=copyWithRoute(s,s.getRoute());candidate.setIsMaintenanceBlocked(false);checkConflict(candidate,scheduleRepository.findAll()); }
        s.setIsMaintenanceBlocked(blocked);s.setMaintenanceNotes(clean);scheduleRepository.save(s);return maintenanceMessage(s,null,blocked);
    }
    private String maintenanceMessage(Schedule s,LocalDate date,boolean blocked) {
        if(!blocked) return "Maintenance block lifted. New reservations use current service eligibility.";
        long affected=bookingRepository.findByScheduleId(s.getId()).stream().filter(b->b.getStatus()!=Booking.BookingStatus.CANCELLED && b.getTravelDate()!=null
            && !b.getTravelDate().isBefore(LocalDate.now()) && (date==null || date.equals(b.getTravelDate()))).count();
        return affected>0 ? "WARNING: Maintenance blocks new reservations; "+affected+" existing booking(s) need coordinator review. Their records and payments are retained." : "Maintenance saved. New reservations are blocked for the affected service date(s).";
    }
    @Transactional(readOnly=true) public List<ScheduleOverride> getOverrides(Long scheduleId) { return List.copyOf(getScheduleById(scheduleId).getOverrides()); }
    @Transactional(readOnly=true) public OverrideDto getOverrideDto(Long scheduleId,Long id) {
        ScheduleOverride o=getScheduleById(scheduleId).getOverrides().stream().filter(v->Objects.equals(v.getId(),id)).findFirst().orElseThrow(()->new ResourceNotFoundException("Dated change not found."));
        OverrideDto dto=new OverrideDto();dto.setServiceDate(o.getServiceDate().toString());dto.setKind(o.getKind().name());dto.setLabel(o.getLabel());
        dto.setDepartureTime(o.getDepartureTime()==null ? null : o.getDepartureTime().toString());dto.setArrivalTime(o.getArrivalTime()==null ? null : o.getArrivalTime().toString());
        dto.setArrivalDayOffset(o.getArrivalDayOffset());dto.setFirstClassFare(o.getFirstClassFare());dto.setSecondClassFare(o.getSecondClassFare());return dto;
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public String saveOverride(Long scheduleId,Long id,OverrideDto dto) {
        lockCatalog();Schedule s=locked(scheduleId);LocalDate date;
        try { date=LocalDate.parse(dto.getServiceDate()==null ? "" : dto.getServiceDate()); } catch(java.time.format.DateTimeParseException invalid) { throw new OperationValidation("serviceDate","Choose a valid date."); }
        if(date.isBefore(LocalDate.now())) OperationalRules.fail("serviceDate","Choose today or a future date; past service records cannot be changed.");
        ScheduleOverride.Kind kind;try { kind=ScheduleOverride.Kind.valueOf(dto.getKind()); } catch(Exception invalid) { throw new OperationValidation("kind","Choose a timetable override or planned maintenance."); }
        ScheduleOverride existing=id==null ? null : s.getOverrides().stream().filter(o->Objects.equals(o.getId(),id)).findFirst().orElseThrow(()->new ResourceNotFoundException("Dated change not found."));
        if(existing!=null && existing.getServiceDate().isBefore(LocalDate.now())) throw new IllegalArgumentException("Past dated changes must be retained for historical reference.");
        if(s.getOverrides().stream().anyMatch(o->(id==null || !Objects.equals(o.getId(),id)) && date.equals(o.getServiceDate()) && kind==o.getKind())) OperationalRules.fail("serviceDate","This change type already exists for this service/date. Edit that change instead.");
        if(existing!=null && existing.getKind()==ScheduleOverride.Kind.TIMETABLE && bookedOn(s,existing.getServiceDate())) throw new IllegalArgumentException("This dated timetable has booking history and must be retained.");
        if(kind==ScheduleOverride.Kind.TIMETABLE && bookedOn(s,date)) throw new IllegalArgumentException("Bookings already exist for this date. Keep their timetable and review the affected passengers before planning a replacement service.");
        ScheduleOverride next=new ScheduleOverride();next.setId(id);next.setSchedule(s);next.setServiceDate(date);next.setKind(kind);next.setLabel(OperationalRules.note(dto.getLabel(),"label",kind==ScheduleOverride.Kind.TIMETABLE ? 100 : 300));
        if(kind==ScheduleOverride.Kind.TIMETABLE) {
            next.setDepartureTime(OperationalRules.time(dto.getDepartureTime(),"departureTime"));next.setArrivalTime(OperationalRules.time(dto.getArrivalTime(),"arrivalTime"));next.setArrivalDayOffset(dto.getArrivalDayOffset());
            int length=OperationalRules.duration(next.getDepartureTime(),next.getArrivalTime(),next.getArrivalDayOffset(),"arrivalTime");
            if(s.getStops().stream().anyMatch(stop->stop.getDepartureOffset()>=length)) OperationalRules.fail("arrivalTime","The override must allow time for all configured intermediate stops.");
            next.setFirstClassFare(OperationalRules.fare(dto.getFirstClassFare(),"firstClassFare",false));next.setSecondClassFare(OperationalRules.fare(dto.getSecondClassFare(),"secondClassFare",false));
            if(!date.atTime(next.getDepartureTime()).isAfter(LocalDateTime.now())) OperationalRules.fail("departureTime","Choose a departure that has not passed.");
        }
        Schedule check=copyWithRoute(s,s.getRoute());check.setOverrides(new ArrayList<>(s.getOverrides().stream().filter(o->!Objects.equals(o.getId(),id) || id==null).toList()));check.getOverrides().add(next);checkConflict(check,scheduleRepository.findAll());
        if(existing==null) s.getOverrides().add(next); else {
            existing.setServiceDate(date);existing.setKind(kind);existing.setLabel(next.getLabel());existing.setDepartureTime(next.getDepartureTime());existing.setArrivalTime(next.getArrivalTime());
            existing.setArrivalDayOffset(next.getArrivalDayOffset());existing.setFirstClassFare(next.getFirstClassFare());existing.setSecondClassFare(next.getSecondClassFare());
        }
        scheduleRepository.saveAndFlush(s);return kind==ScheduleOverride.Kind.MAINTENANCE ? maintenanceMessage(s,date,true) : "Dated timetable saved. Search and booking use this departure on "+date+".";
    }
    private boolean bookedOn(Schedule s,LocalDate date) { return bookingRepository.findByScheduleId(s.getId()).stream().anyMatch(b->Objects.equals(b.getTravelDate(),date)); }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED) public void deleteOverride(Long scheduleId,Long id) {
        lockCatalog();Schedule s=locked(scheduleId);ScheduleOverride o=s.getOverrides().stream().filter(v->Objects.equals(v.getId(),id)).findFirst().orElseThrow(()->new ResourceNotFoundException("Dated change not found."));
        if(o.getServiceDate().isBefore(LocalDate.now()) || (o.getKind()==ScheduleOverride.Kind.TIMETABLE && bookedOn(s,o.getServiceDate()))) throw new IllegalArgumentException("This dated timetable is historical or has bookings and must be retained.");
        Schedule check=copyWithRoute(s,s.getRoute());check.setOverrides(new ArrayList<>(s.getOverrides().stream().filter(v->!Objects.equals(v.getId(),id)).toList()));checkConflict(check,scheduleRepository.findAll());
        s.getOverrides().remove(o);scheduleRepository.save(s);
    }
    private ScheduleDto mapToDto(Schedule s) {
        var dto=ScheduleDto.builder().id(s.getId()).trainId(s.getTrain()==null ? null : s.getTrain().getId()).trainName(s.getTrain()==null ? null : s.getTrain().getTrainName())
            .trainNumber(s.getTrain()==null ? null : s.getTrain().getTrainNumber()).routeId(s.getRoute()==null ? null : s.getRoute().getId())
            .origin(s.getRoute()==null ? null : s.getRoute().getOrigin()).destination(s.getRoute()==null ? null : s.getRoute().getDestination())
            .departureTime(s.getDepartureTime()==null ? null : s.getDepartureTime().toString()).arrivalTime(s.getArrivalTime()==null ? null : s.getArrivalTime().toString()).arrivalDayOffset(offset(s))
            .dayOfWeek(s.getDayOfWeek()==null ? null : s.getDayOfWeek().name()).firstClassFare(s.getFirstClassFare()).secondClassFare(s.getSecondClassFare())
            .farePolicy(policy(s)).isActive(s.getIsActive()).defaultPlatform(s.getRoute()==null ? null : s.getRoute().getDefaultPlatform())
            .departurePlatform(ServicePlan.resolve(s,LocalDate.now()).departurePlatform()).arrivalPlatform(ServicePlan.resolve(s,LocalDate.now()).arrivalPlatform()==null ? "Platform 1" : ServicePlan.resolve(s,LocalDate.now()).arrivalPlatform())
            .isSeasonal(s.getIsSeasonal()).seasonalName(s.getSeasonalName()).isMaintenanceBlocked(s.getIsMaintenanceBlocked()).maintenanceNotes(s.getMaintenanceNotes()).build();
        dto.setStops(s.getStops().stream().map(stop->new StopDto(stop.getStation(),s.getDepartureTime().plusMinutes(stop.getArrivalOffset()).toString(),s.getDepartureTime().plusMinutes(stop.getDepartureOffset()).toString(),
            (s.getDepartureTime().toSecondOfDay()/60+stop.getArrivalOffset())/1440,stop.getPlatform())).collect(java.util.stream.Collectors.toCollection(ArrayList::new)));return dto;
    }
}
