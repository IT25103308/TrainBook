package com.trainbooking.it25102327.repository;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.dto.*;

import com.trainbooking.it25102327.model.Train;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Train} entity management.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Repository
public interface TrainRepository extends JpaRepository<Train, Long> {

    /**
     * Finds trains matching a specific operational status.
     *
     * @param status the operational status
     * @return list of trains matching status
     */
    List<Train> findByStatus(Train.TrainStatus status);

    /**
     * Finds a train by its unique train identification number.
     *
     * @param trainNumber the train number code
     * @return an {@link Optional} containing the train if found
     */
    Optional<Train> findByTrainNumber(String trainNumber);
    Optional<Train> findByTrainNumberIgnoreCase(String trainNumber);
}
