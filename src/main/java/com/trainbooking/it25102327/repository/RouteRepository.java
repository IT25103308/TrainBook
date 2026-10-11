package com.trainbooking.it25102327.repository;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.dto.*;

import com.trainbooking.it25102327.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Route} entity management.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    /**
     * Finds routes connecting the specified origin and destination stations.
     *
     * @param origin departure station
     * @param destination destination station
     * @return list of matching routes
     */
    List<Route> findByOriginAndDestination(String origin, String destination);
}
