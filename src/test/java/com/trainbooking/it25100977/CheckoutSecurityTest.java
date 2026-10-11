package com.trainbooking.it25100977;

import com.trainbooking.config.SecurityConfig;
import com.trainbooking.it25100977.controller.PaymentController;
import com.trainbooking.it25100977.service.PaymentService;
import com.trainbooking.it25103308.controller.BookingController;
import com.trainbooking.it25103308.service.BookingService;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.service.PassengerService;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Loads the real security chain with mock services, never the application or its database. */
class CheckoutSecurityTest {
    @Configuration @EnableWebMvc @Import({SecurityConfig.class,GlobalExceptionHandler.class})
    static class WebOnly {
        @Bean UserDetailsService users() { return new InMemoryUserDetailsManager(); }
        @Bean BookingService bookings() { return mock(BookingService.class); }
        @Bean PaymentService payments() { return mock(PaymentService.class); }
        @Bean ScheduleRepository schedules() { return mock(ScheduleRepository.class); }
        @Bean BookingController bookingController(BookingService bookings, ScheduleRepository schedules) { return new BookingController(bookings,schedules,mock(PassengerService.class)); }
        @Bean PaymentController paymentController(PaymentService payments,BookingService bookings) { return new PaymentController(payments,bookings); }
    }
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext()); context.register(WebOnly.class); context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(context.getBean(BookingService.class).getBookingById(24L)).thenReturn(Booking.builder().id(24L).passenger(User.builder().email("own@test.invalid").build())
            .status(Booking.BookingStatus.PENDING).lockExpiresAt(LocalDateTime.now().plusMinutes(3)).build());
    }
    @AfterEach void cleanup() { context.close(); }
    @Test void privateBookingTicketAndQrRoutesRequireAuthentication() throws Exception {
        for(String path:new String[]{"/booking/24/payment","/booking/ticket/24","/payment/ticket/10/download","/api/tickets/10/qr"}) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        }
    }
    @Test void publicSeatSelectionRemainsAccessible() throws Exception {
        var date=java.time.LocalDate.now().plusDays(1);
        when(context.getBean(ScheduleRepository.class).findById(2L)).thenReturn(java.util.Optional.of(
            com.trainbooking.it25102327.model.Schedule.builder().id(2L).build()));
        when(context.getBean(BookingService.class).getSeatAvailability(eq(2L),eq(date),anyString()))
            .thenReturn(new com.trainbooking.it25103308.dto.SeatAvailability(10,10,java.util.List.of(),null));
        mvc.perform(get("/booking/seats").param("scheduleId","2").param("date",date.toString()))
            .andExpect(status().isOk()).andExpect(view().name("it25103308/seat-select"));
    }
    @Test void authenticationDoesNotPermitAccessToAnotherPassengersBooking() throws Exception {
        mvc.perform(get("/booking/24/payment").with(user("other@test.invalid"))).andExpect(status().isNotFound());
    }
    @Test void searchClassIsCarriedToSeatSelection() throws Exception {
        var date=java.time.LocalDate.now().plusDays(1);
        when(context.getBean(ScheduleRepository.class).findById(2L)).thenReturn(java.util.Optional.of(
            com.trainbooking.it25102327.model.Schedule.builder().id(2L).build()));
        when(context.getBean(BookingService.class).getSeatAvailability(eq(2L),eq(date),anyString()))
            .thenReturn(new com.trainbooking.it25103308.dto.SeatAvailability(10,10,java.util.List.of(),null));
        var result=mvc.perform(get("/booking/seats").param("scheduleId","2").param("date",date.toString()).param("seatClass","SECOND"))
            .andExpect(status().isOk()).andReturn();
        var request=(com.trainbooking.it25103308.dto.BookingRequest)result.getModelAndView().getModel().get("bookingRequest");
        assertEquals("SECOND",request.getSeatClass());
    }
    @Test void allClassesStartsWithAnAvailableClassRatherThanSoldOutFirstClass() throws Exception {
        var date=java.time.LocalDate.now().plusDays(1);
        when(context.getBean(ScheduleRepository.class).findById(2L)).thenReturn(java.util.Optional.of(
            com.trainbooking.it25102327.model.Schedule.builder().id(2L).build()));
        when(context.getBean(BookingService.class).getSeatAvailability(2L,date,"FIRST"))
            .thenReturn(new com.trainbooking.it25103308.dto.SeatAvailability(10,0,java.util.List.of(),null));
        when(context.getBean(BookingService.class).getSeatAvailability(2L,date,"SECOND"))
            .thenReturn(new com.trainbooking.it25103308.dto.SeatAvailability(10,10,java.util.List.of(),null));
        var result=mvc.perform(get("/booking/seats").param("scheduleId","2").param("date",date.toString()))
            .andExpect(status().isOk()).andReturn();
        var request=(com.trainbooking.it25103308.dto.BookingRequest)result.getModelAndView().getModel().get("bookingRequest");
        assertEquals("SECOND",request.getSeatClass());
    }
    @Test void cancellationStillRequiresCsrfAndOwnership() throws Exception {
        mvc.perform(post("/booking/24/cancel").with(user("own@test.invalid"))).andExpect(status().isForbidden());
        mvc.perform(post("/booking/24/cancel").with(user("other@test.invalid")).with(csrf())).andExpect(status().isNotFound());
        verify(context.getBean(BookingService.class),never()).releaseLock(any());
    }
}
