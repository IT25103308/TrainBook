package com.trainbooking.it25102925;

import com.trainbooking.it25102327.service.TrainService;
import com.trainbooking.it25102925.controller.NotificationController;
import com.trainbooking.it25102925.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private TrainService trainService;

    @Mock private com.trainbooking.it25101520.repository.UserRepository userRepository;
    @Mock private com.trainbooking.it25102925.service.ServiceStatusService statusService;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    @DisplayName("Should return user live map view without admin sidebar for unauthenticated visitors")
    void showLiveMap_Unauthenticated_ReturnsUserView() {
        Model model = new ConcurrentModel();
        String view = notificationController.showLiveMap(null, null, model);
        assertEquals("it25102925/live-map", view);
    }

    @Test
    @DisplayName("Should return user live map view without admin sidebar for regular passengers")
    void showLiveMap_Passenger_ReturnsUserView() {
        Model model = new ConcurrentModel();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "passenger@example.com", "password",
                List.of(new SimpleGrantedAuthority("ROLE_PASSENGER"))
        );

        String view = notificationController.showLiveMap(null, auth, model);
        assertEquals("it25102925/live-map", view);
    }

    @Test
    @DisplayName("Should return admin live map view with sidebar for ADMIN role")
    void showLiveMap_Admin_ReturnsAdminView() {
        Model model = new ConcurrentModel();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@trainbooking.lk", "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        String view = notificationController.showLiveMap(null, auth, model);
        assertEquals("it25102925/admin-live-map", view);
    }

    @Test
    @DisplayName("Should return admin live map view with sidebar for STATION_STAFF role")
    void showLiveMap_Staff_ReturnsAdminView() {
        Model model = new ConcurrentModel();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "staff@trainbooking.lk", "password",
                List.of(new SimpleGrantedAuthority("ROLE_STATION_STAFF"))
        );

        String view = notificationController.showLiveMap(null, auth, model);
        assertEquals("it25102925/admin-live-map", view);
    }

    @Test
    @DisplayName("Should return user view if view=passenger is explicitly requested even for admin")
    void showLiveMap_ExplicitPassengerView_ReturnsUserView() {
        Model model = new ConcurrentModel();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@trainbooking.lk", "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        String view = notificationController.showLiveMap("passenger", auth, model);
        assertEquals("it25102925/live-map", view);
    }

    @Test
    @DisplayName("Should return admin view for showAdminLiveMap")
    void showAdminLiveMap_ReturnsAdminView() {
        Model model = new ConcurrentModel();
        String view = notificationController.showAdminLiveMap(model);
        assertEquals("it25102925/admin-live-map", view);
    }
}
