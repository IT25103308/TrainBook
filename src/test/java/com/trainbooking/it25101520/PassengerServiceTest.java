package com.trainbooking.it25101520;

import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25101520.dto.FavoriteRouteDto;
import com.trainbooking.it25101520.dto.UserProfileDto;
import com.trainbooking.it25101520.model.FavoriteRoute;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.FavoriteRouteRepository;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25101520.service.PassengerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassengerServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FavoriteRouteRepository favoriteRouteRepository;

    @Mock private com.trainbooking.it25102327.repository.RouteRepository routeRepository;

    @InjectMocks
    private PassengerService passengerService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .firstName("Kasun")
                .lastName("Perera")
                .email("kasun@example.com")
                .role(User.Role.PASSENGER)
                .phone("0771234567")
                .build();
    }

    @Test
    @DisplayName("Should retrieve user by email")
    void testGetUserByEmail_Success() {
        when(userRepository.findByEmail("kasun@example.com")).thenReturn(Optional.of(sampleUser));

        User user = passengerService.getUserByEmail("kasun@example.com");

        assertNotNull(user);
        assertEquals("Kasun", user.getFirstName());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user email does not exist")
    void testGetUserByEmail_NotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                passengerService.getUserByEmail("unknown@example.com")
        );
    }

    @Test
    @DisplayName("Should update passenger profile details")
    void testUpdateProfile_Success() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserProfileDto updateDto = UserProfileDto.builder()
                .firstName("Kasun Updated")
                .lastName("Perera Updated")
                .phone("0779998877").delayAlertThresholdMinutes(15)
                .build();

        User updated = passengerService.updateProfile(1L, updateDto);

        assertEquals("Kasun Updated", updated.getFirstName());
        assertEquals("Kasun Updated", sampleUser.getFirstName());
        assertEquals("0779998877", sampleUser.getPhone());
        verify(userRepository).save(sampleUser);
    }

    @Test
    @DisplayName("Should add a favorite route for passenger")
    void testAddFavoriteRoute_Success() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sampleUser));
        when(routeRepository.findAll()).thenReturn(List.of(com.trainbooking.it25102327.model.Route.builder().origin("Colombo Fort").destination("Kandy").build()));
        when(favoriteRouteRepository.save(any(FavoriteRoute.class))).thenAnswer(i -> {
            FavoriteRoute r = i.getArgument(0);
            r.setId(100L);
            return r;
        });

        FavoriteRouteDto dto = FavoriteRouteDto.builder()
                .origin("Colombo Fort")
                .destination("Kandy")
                .build();

        FavoriteRoute result = passengerService.addFavoriteRoute(1L, dto);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("Colombo Fort", result.getOrigin());
        assertEquals("Kandy", result.getDestination());
        assertEquals(sampleUser, result.getUser());
    }

    @Test
    @DisplayName("Should return list of saved favorite routes")
    void testGetFavoriteRoutes() {
        FavoriteRoute fav = FavoriteRoute.builder().id(5L).origin("Colombo").destination("Galle").user(sampleUser).build();
        when(favoriteRouteRepository.findByUserId(1L)).thenReturn(List.of(fav));

        List<FavoriteRoute> routes = passengerService.getFavoriteRoutes(1L);

        assertEquals(1, routes.size());
        assertEquals("Galle", routes.get(0).getDestination());
    }

    @Test
    @DisplayName("Should update and retrieve notification preferences")
    void testUpdateAndGetNotificationPreferences() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserProfileDto updateDto = UserProfileDto.builder()
                .firstName("Kasun")
                .lastName("Perera")
                .phone("0771234567")
                .notifyByEmail(true)
                .notifyBySms(false)
                .delayAlertThresholdMinutes(30)
                .build();

        User updated = passengerService.updateProfile(1L, updateDto);
        assertTrue(updated.getNotifyByEmail());
        assertFalse(updated.getNotifyBySms());
        assertEquals(30, updated.getDelayAlertThresholdMinutes());

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        UserProfileDto profileDto = passengerService.getUserProfile(1L);
        assertNotNull(profileDto);
        assertTrue(profileDto.getNotifyByEmail());
        assertFalse(profileDto.getNotifyBySms());
        assertEquals(30, profileDto.getDelayAlertThresholdMinutes());
    }
}
