package com.trainbooking.it25102925.repository;
import com.trainbooking.it25102925.model.ServiceDisruption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;
public interface ServiceDisruptionRepository extends JpaRepository<ServiceDisruption,Long> {
    Optional<ServiceDisruption> findByScheduleIdAndServiceDate(Long scheduleId,LocalDate date);
    List<ServiceDisruption> findByServiceDateOrderByScheduleIdAsc(LocalDate date);
}
