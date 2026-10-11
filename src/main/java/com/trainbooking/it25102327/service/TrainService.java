package com.trainbooking.it25102327.service;

import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25102327.dto.TrainDto;
import com.trainbooking.it25102327.dto.TrainSearchResultDto;
import com.trainbooking.it25102327.model.Route;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.model.Train;
import com.trainbooking.it25102327.repository.RouteRepository;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25102327.repository.TrainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import com.trainbooking.it25103308.service.BookingService;
import java.util.List;

/**
 * Service class handling train fleet management, schedules, live status updates, and search operations.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainService {

    private final TrainRepository trainRepository;
    private final RouteRepository routeRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleService scheduleService;
    private final BookingService bookingService;

    /**
     * Searches for available train schedules based on origin, destination, and travel date.
     *
     * @param origin departure station
     * @param destination destination station
     * @param date date of travel
     * @return list of matching {@link TrainSearchResultDto} records
     */
    @Transactional(readOnly = true)
    public List<TrainSearchResultDto> searchTrains(String origin, String destination, LocalDate date) {
        return searchTrains(origin, destination, date, "");
    }

    /** Station choices come from saved routes, including routes added by coordinators. */
    @Transactional(readOnly = true)
    public List<String> getStations() {
        var stations = new java.util.TreeMap<String, String>();
        routeRepository.findAll().stream().flatMap(r -> Stream.of(r.getOrigin(), r.getDestination()))
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim).sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(value -> stations.putIfAbsent(value.toLowerCase(Locale.ROOT), value));
        return List.copyOf(stations.values());
    }

    @Transactional(readOnly = true)
    public List<TrainSearchResultDto> searchTrains(String origin, String destination, LocalDate date, String seatClass) {
        List<String> stations = getStations();
        String cleanOrigin = stationChoice(origin, stations, "origin");
        String cleanDestination = stationChoice(destination, stations, "destination");
        if (cleanOrigin.equalsIgnoreCase(cleanDestination)) {
            throw new IllegalArgumentException("Choose different origin and destination stations.");
        }
        LocalDate travelDate = date == null ? LocalDate.now() : date;
        if (travelDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Choose today or a future travel date.");
        }
        String selectedClass = seatClass == null ? "" : seatClass.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("", "FIRST", "SECOND").contains(selectedClass)) {
            throw new IllegalArgumentException("Choose All Classes, 1st Class or 2nd Class.");
        }

        // Never substitute a different weekday or a partial station-name match.
        List<Schedule> schedules = scheduleRepository.findMatchingSchedulesOnDate(
                cleanOrigin, cleanDestination, travelDate.getDayOfWeek(), travelDate);
        List<TrainSearchResultDto> results = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        for (Schedule sch : schedules) {
            Train train = sch.getTrain();
            Route route = sch.getRoute();
            ServicePlan plan=ServicePlan.resolve(sch,travelDate);
            if(route==null || plan.arrival()==null || !plan.availableAt(java.time.LocalDateTime.now())) continue;
            java.math.BigDecimal firstFare=null,secondFare=null;
            try { firstFare=plan.fare("FIRST"); } catch(IllegalArgumentException invalidFare) { }
            try { secondFare=plan.fare("SECOND"); } catch(IllegalArgumentException invalidFare) { }
            int first=firstFare!=null ? bookingService.getAvailableSeats(sch.getId(),travelDate,"FIRST") : 0;
            int second=secondFare!=null ? bookingService.getAvailableSeats(sch.getId(),travelDate,"SECOND") : 0;
            if (first == 0 && second == 0) continue;
            java.math.BigDecimal fare = selectedClass.equals("FIRST") && first > 0 ? firstFare
                    : selectedClass.equals("SECOND") && second > 0 ? secondFare
                    : first == 0 ? secondFare
                    : second == 0 ? firstFare
                    : firstFare.min(secondFare);
            results.add(TrainSearchResultDto.builder()
                    .scheduleId(sch.getId()).trainName(train.getTrainName()).trainNumber(train.getTrainNumber())
                    .origin(route.getOrigin()).destination(route.getDestination())
                    .departureTime(plan.departure()).arrivalTime(plan.arrival()).arrivalDayOffset(plan.arrivalDayOffset())
                    .availableFirstClass(first).availableSecondClass(second)
                    .firstClassFare(firstFare).secondClassFare(secondFare)
                    .displayFare(fare).selectedClass(selectedClass)
                    .status(plan.status()).build());
        }
        results.sort(Comparator.comparing(TrainSearchResultDto::isSelectedClassAvailable).reversed()
                .thenComparing(TrainSearchResultDto::getDepartureTime).thenComparing(TrainSearchResultDto::getScheduleId));
        return results;
    }

    private boolean validFare(java.math.BigDecimal fare) {
        return fare != null && fare.signum() >= 0;
    }

    private String stationChoice(String value, List<String> stations, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Choose an " + field + " station.");
        return stations.stream().filter(station -> station.equalsIgnoreCase(value.trim())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Choose the " + field + " from the station list."));
    }

    /**
     * Retrieves all trains in the system.
     *
     * @return list of all {@link Train} entities
     */
    public List<Train> getAllTrains() {
        log.info("Retrieving all trains");
        return trainRepository.findAll();
    }

    /**
     * Retrieves a single train by its ID.
     *
     * @param id train ID
     * @return {@link Train} entity
     */
    public Train getTrainById(Long id) {
        log.info("Fetching train by ID: {}", id);
        return trainRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Train not found with ID: " + id));
    }

    /**
     * Creates and saves a new train record.
     *
     * @param dto train details
     * @return created {@link Train} entity
     */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Train createTrain(TrainDto dto) {
        log.info("Creating new train: {}", dto.getTrainNumber());
        scheduleService.lockCatalog();
        OperationalRules.train(dto);
        trainRepository.findByTrainNumberIgnoreCase(dto.getTrainNumber()).ifPresent(existing->{throw new OperationValidation("trainNumber","This train number already exists.");});
        Train.TrainStatus status=parseStatus(dto.getStatus());
        int totalSeats=dto.getTotalSeats();

        Train train = Train.builder()
                .trainNumber(dto.getTrainNumber())
                .trainName(dto.getTrainName())
                .trainType(dto.getTrainType() != null && !dto.getTrainType().isBlank() ? dto.getTrainType() : "Express Intercity")
                .totalSeats(totalSeats)
                .firstClassSeats(dto.getFirstClassSeats() != null ? dto.getFirstClassSeats() : 0)
                .secondClassSeats(dto.getSecondClassSeats() != null ? dto.getSecondClassSeats() : 0)
                .status(status)
                .build();

        return trainRepository.save(train);
    }

    /**
     * Updates an existing train record.
     *
     * @param id train ID
     * @param dto updated details
     * @return updated {@link Train} entity
     */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Train updateTrain(Long id, TrainDto dto) {
        log.info("Updating train ID: {}", id);
        scheduleService.lockCatalog();
        Train train=getTrainById(id);OperationalRules.train(dto);Train.TrainStatus status=parseStatus(dto.getStatus());
        trainRepository.findByTrainNumberIgnoreCase(dto.getTrainNumber()).ifPresent(existing->{
            if(!existing.getId().equals(id)) throw new OperationValidation("trainNumber","This train number already exists.");
        });
        scheduleService.guardTrainEdit(train,dto);
        train.setTrainNumber(dto.getTrainNumber());train.setTrainName(dto.getTrainName());train.setTrainType(dto.getTrainType());
        train.setFirstClassSeats(dto.getFirstClassSeats());train.setSecondClassSeats(dto.getSecondClassSeats());train.setTotalSeats(dto.getTotalSeats());train.setStatus(status);

        return trainRepository.save(train);
    }

    /**
     * Deletes a train by its ID.
     * Deletes only unused trains; referenced services and passenger history are retained.
     *
     * @param id train ID
     */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void deleteTrain(Long id) {
        scheduleService.lockCatalog();Train train=getTrainById(id);
        if(!scheduleRepository.findByTrainId(id).isEmpty() || routeRepository.findAll().stream().anyMatch(r->r.getTrain()!=null && java.util.Objects.equals(r.getTrain().getId(),id)))
            throw new IllegalArgumentException("This train is used by routes or timetables. Retain it and use Inactive status to stop new reservations.");
        trainRepository.delete(train);
    }

    /**
     * Updates the operational status of a train (ON_TIME, DELAYED, CANCELLED).
     *
     * @param id train ID
     * @param status new status string
     * @return updated {@link Train} entity
     */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Train updateTrainStatus(Long id, String status) {
        log.info("Updating status for train ID {} to {}", id, status);
        scheduleService.lockCatalog();scheduleService.lockTrainInventory(id);
        Train train = getTrainById(id);
        train.setStatus(parseStatus(status));
        return trainRepository.save(train);
    }

    private Train.TrainStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return Train.TrainStatus.ON_TIME;
        }
        String s = statusStr.trim().toUpperCase(Locale.ROOT);
        if (s.equals("ACTIVE")) return Train.TrainStatus.ON_TIME;
        try {
            return Train.TrainStatus.valueOf(s);
        } catch (IllegalArgumentException ex) {
            throw new OperationValidation("status","Choose a valid train status.");
        }
    }
}
