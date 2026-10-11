package com.trainbooking.it25101520;

import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25102925.model.Notification;
import com.trainbooking.it25102925.repository.NotificationRepository;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TemplateRenderingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("Should successfully parse and render booking-history.html template with actual bookings")
    @WithMockUser(username = "admin@trainbooking.lk", roles = {"ADMIN", "PASSENGER"})
    void bookingHistoryTemplate_RendersWithoutParsingError() throws Exception {
        User admin = userRepository.findByEmail("admin@trainbooking.lk").orElse(null);
        Schedule schedule = scheduleRepository.findAll().stream().findFirst().orElse(null);

        if (admin != null && schedule != null) {
            Booking sampleBooking = Booking.builder()
                    .passenger(admin)
                    .schedule(schedule)
                    .travelDate(LocalDate.now().plusDays(2))
                    .seatClass(Booking.SeatClass.FIRST)
                    .numberOfSeats(2)
                    .totalAmount(new BigDecimal("3000.00"))
                    .status(Booking.BookingStatus.CONFIRMED)
                    .passengerName("System Administrator")
                    .bookedAt(LocalDateTime.now())
                    .build();
            bookingRepository.save(sampleBooking);
        }

        mockMvc.perform(get("/passenger/bookings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My Bookings & Tickets")))
                .andExpect(content().string(containsString("All Bookings")));
    }

    @Test
    @DisplayName("Should successfully parse and render list.html template with actual notifications")
    @WithMockUser(username = "admin@trainbooking.lk", roles = {"ADMIN", "PASSENGER"})
    void notificationListTemplate_RendersWithoutParsingError() throws Exception {
        User admin = userRepository.findByEmail("admin@trainbooking.lk").orElse(null);
        if (admin != null) {
            Notification sampleNotif = Notification.builder()
                    .recipient(admin)
                    .subject("Sri Lanka Railways Alert: Udarata Menike (1015) DELAYED by 15 mins")
                    .message("Express Train #1015 is running with an estimated delay of 15 minutes.")
                    .type(Notification.NotificationType.IN_APP)
                    .isRead(false)
                    .sentAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(sampleNotif);
        }

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Notifications & Travel Alerts")));
    }

    @Test
    @DisplayName("Should process mark-all-read and redirect to /notifications")
    @WithMockUser(username = "admin@trainbooking.lk", roles = {"ADMIN", "PASSENGER"})
    void markAllNotificationsAsRead_RedirectsSuccessfully() throws Exception {
        mockMvc.perform(post("/notifications/mark-all-read").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"));
    }
}
