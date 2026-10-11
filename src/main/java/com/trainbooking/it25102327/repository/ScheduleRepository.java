package com.trainbooking.it25102327.repository;

import com.trainbooking.it25102327.model.Schedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Schedule} entity management.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="overrides")
    @Query("SELECT s FROM Schedule s JOIN FETCH s.train t JOIN FETCH s.route r WHERE LOWER(TRIM(r.origin))=LOWER(:origin) AND LOWER(TRIM(r.destination))=LOWER(:destination) AND s.isActive=true AND (s.dayOfWeek=:day OR EXISTS (SELECT o.id FROM ScheduleOverride o WHERE o.schedule=s AND o.serviceDate=:date))")
    List<Schedule> findMatchingSchedulesOnDate(@Param("origin") String origin,@Param("destination") String destination,@Param("day") DayOfWeek day,@Param("date") java.time.LocalDate date);

    /** Serializes new reservations for this schedule until their transaction commits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Schedule s WHERE s.id = :id")
    Optional<Schedule> findByIdForUpdate(@Param("id") Long id);

    /**
     * Finds active schedules for routes between the specified origin and destination.
     *
     * @param origin departure station
     * @param destination destination station
     * @param isActive schedule active status
     * @return list of matching train schedules
     */
    List<Schedule> findByRouteOriginAndRouteDestinationAndIsActive(String origin, String destination, Boolean isActive);

    /**
     * Searches for active schedules matching origin, destination and specific day of week.
     *
     * @param origin departure station
     * @param destination destination station
     * @param dayOfWeek day of week of travel
     * @return list of matching train schedules
     */
    @Query("SELECT s FROM Schedule s JOIN FETCH s.train t JOIN FETCH s.route r " +
           "WHERE LOWER(TRIM(r.origin)) = LOWER(:origin) " +
           "AND LOWER(TRIM(r.destination)) = LOWER(:destination) " +
           "AND s.dayOfWeek = :dayOfWeek " +
           "AND s.isActive = true")
    List<Schedule> findMatchingSchedules(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("dayOfWeek") DayOfWeek dayOfWeek
    );

    /**
     * Searches for active schedules matching origin and destination regardless of day.
     *
     * @param origin departure station
     * @param destination destination station
     * @return list of matching train schedules
     */
    @Query("SELECT s FROM Schedule s JOIN FETCH s.train t JOIN FETCH s.route r " +
           "WHERE LOWER(r.origin) LIKE LOWER(CONCAT('%', :origin, '%')) " +
           "AND LOWER(r.destination) LIKE LOWER(CONCAT('%', :destination, '%')) " +
           "AND s.isActive = true")
    List<Schedule> findMatchingSchedulesAnyDay(
            @Param("origin") String origin,
            @Param("destination") String destination
    );

    /**
     * Finds all schedules associated with a given train ID.
     */
    List<Schedule> findByTrainId(Long trainId);
}
