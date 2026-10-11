package com.trainbooking.it25101520.service;

import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25101520.dto.FavoriteRouteDto;
import com.trainbooking.it25101520.dto.UserProfileDto;
import com.trainbooking.it25101520.model.FavoriteRoute;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.FavoriteRouteRepository;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.model.Route;
import com.trainbooking.it25102327.repository.RouteRepository;
import com.trainbooking.validation.ContactRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PassengerService {
    private final UserRepository userRepository;
    private final FavoriteRouteRepository favoriteRouteRepository;
    private final RouteRepository routeRepository;

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(ContactRules.email(email))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
    }

    @Transactional
    public User updateProfile(Long userId, UserProfileDto dto) {
        String first = ContactRules.name(dto.getFirstName(), "First name");
        String last = ContactRules.name(dto.getLastName(), "Last name");
        String phone = ContactRules.phone(dto.getPhone());
        AccountFormRules.threshold(dto.getDelayAlertThresholdMinutes());
        User user = lockUser(userId);
        user.setFirstName(first); user.setLastName(last); user.setPhone(phone);
        user.setNotifyByEmail(Boolean.TRUE.equals(dto.getNotifyByEmail()));
        user.setNotifyBySms(Boolean.TRUE.equals(dto.getNotifyBySms()));
        user.setDelayAlertThresholdMinutes(dto.getDelayAlertThresholdMinutes());
        return userRepository.save(user);
    }

    public UserProfileDto getUserProfile(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Account not found."));
        return UserProfileDto.builder().firstName(user.getFirstName()).lastName(user.getLastName())
                .email(user.getEmail()).phone(user.getPhone())
                .notifyByEmail(Boolean.TRUE.equals(user.getNotifyByEmail()))
                .notifyBySms(Boolean.TRUE.equals(user.getNotifyBySms()))
                .delayAlertThresholdMinutes(user.getDelayAlertThresholdMinutes() == null ? 15 : user.getDelayAlertThresholdMinutes()).build();
    }

    public List<FavoriteRoute> getFavoriteRoutes(Long userId) {
        return favoriteRouteRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(FavoriteRoute::getSavedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(FavoriteRoute::getId, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    public List<String> getStationNames() {
        return routeRepository.findAll().stream().flatMap(r -> java.util.stream.Stream.of(r.getOrigin(),r.getDestination()))
                .filter(Objects::nonNull).map(String::strip).filter(s -> !s.isBlank() && s.length() <= 100)
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public FavoriteRoute getFavoriteRoute(Long id, Long userId) {
        if (id == null || id < 1) throw new ResourceNotFoundException("Saved route not found.");
        return favoriteRouteRepository.findByIdAndUserId(id,userId)
                .orElseThrow(() -> new ResourceNotFoundException("Saved route not found."));
    }

    @Transactional
    public FavoriteRoute addFavoriteRoute(Long userId, FavoriteRouteDto dto) {
        Route route = validRoute(dto);
        User user = lockUser(userId);
        if (favoriteRouteRepository.existsByUserIdAndOriginIgnoreCaseAndDestinationIgnoreCase(userId,route.getOrigin().strip(),route.getDestination().strip())) {
            throw new IllegalArgumentException("This route is already in your favourites.");
        }
        return favoriteRouteRepository.save(FavoriteRoute.builder().user(user).origin(route.getOrigin().strip())
                .destination(route.getDestination().strip()).build());
    }

    @Transactional
    public FavoriteRoute updateFavoriteRoute(Long id, Long userId, FavoriteRouteDto dto) {
        lockUser(userId);
        FavoriteRoute favorite = getFavoriteRoute(id,userId);
        Route route = validRoute(dto);
        if (favoriteRouteRepository.existsByUserIdAndOriginIgnoreCaseAndDestinationIgnoreCaseAndIdNot(userId,route.getOrigin().strip(),route.getDestination().strip(),id)) {
            throw new IllegalArgumentException("This route is already in your favourites.");
        }
        favorite.setOrigin(route.getOrigin().strip()); favorite.setDestination(route.getDestination().strip());
        return favoriteRouteRepository.save(favorite);
    }

    @Transactional
    public void removeFavoriteRoute(Long id, Long userId) {
        lockUser(userId);
        favoriteRouteRepository.delete(getFavoriteRoute(id,userId));
    }

    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("Account not found."));
    }

    private Route validRoute(FavoriteRouteDto dto) {
        String origin = dto == null || dto.getOrigin() == null ? "" : dto.getOrigin().strip();
        String destination = dto == null || dto.getDestination() == null ? "" : dto.getDestination().strip();
        if (origin.isEmpty() || destination.isEmpty() || origin.length() > 100 || destination.length() > 100) {
            throw new IllegalArgumentException("Choose an origin and destination from the saved stations.");
        }
        if (origin.equalsIgnoreCase(destination)) throw new IllegalArgumentException("Origin and destination must be different.");
        return routeRepository.findAll().stream().filter(r -> r.getOrigin() != null && r.getDestination() != null
                && r.getOrigin().strip().equalsIgnoreCase(origin) && r.getDestination().strip().equalsIgnoreCase(destination)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Choose a route configured by the railway coordinator. This station pair is not available."));
    }
}
