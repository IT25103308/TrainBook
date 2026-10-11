package com.trainbooking.it25101520.repository;
import com.trainbooking.it25101520.model.*;
import com.trainbooking.it25101520.dto.*;

import com.trainbooking.it25101520.model.FavoriteRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link FavoriteRoute} entity operations.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Repository
public interface FavoriteRouteRepository extends JpaRepository<FavoriteRoute, Long> {

    /**
     * Retrieves all saved favorite routes for a given passenger.
     *
     * @param userId the user ID
     * @return list of favorite routes
     */
    List<FavoriteRoute> findByUserId(Long userId);

    /**
     * Deletes a specific favorite route belonging to a user.
     *
     * @param id the favorite route ID
     * @param userId the user ID
     */
    void deleteByIdAndUserId(Long id, Long userId);

    java.util.Optional<FavoriteRoute> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndOriginIgnoreCaseAndDestinationIgnoreCase(Long userId, String origin, String destination);

    boolean existsByUserIdAndOriginIgnoreCaseAndDestinationIgnoreCaseAndIdNot(Long userId, String origin, String destination, Long id);
}
