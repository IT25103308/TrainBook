package com.trainbooking.it25103308;

import com.trainbooking.config.SecurityConfig;
import com.trainbooking.exception.GlobalExceptionHandler;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.service.PassengerService;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25103308.controller.BookingController;
import com.trainbooking.it25103308.dto.BookingDetailsRequest;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.service.BookingService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.*;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.math.BigDecimal;
import java.time.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingDetailsRenderingTest {
    @Configuration @EnableWebMvc @Import({SecurityConfig.class,GlobalExceptionHandler.class})
    static class WebOnly {
        @Bean org.springframework.security.core.userdetails.UserDetailsService users(){return new org.springframework.security.provisioning.InMemoryUserDetailsManager();}
        @Bean BookingService bookings(){return mock(BookingService.class);}
        @Bean BookingController controller(BookingService b){return new BookingController(b,mock(ScheduleRepository.class),mock(PassengerService.class));}
        @Bean SpringTemplateEngine templates(){var r=new ClassLoaderTemplateResolver();r.setPrefix("templates/");r.setSuffix(".html");r.setTemplateMode("HTML");r.setCharacterEncoding("UTF-8");var e=new SpringTemplateEngine();e.setTemplateResolver(r);e.addDialect(new org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect());return e;}
        @Bean ThymeleafViewResolver views(SpringTemplateEngine e){var v=new ThymeleafViewResolver();v.setTemplateEngine(e);v.setCharacterEncoding("UTF-8");return v;}
    }
    AnnotationConfigWebApplicationContext context;MockMvc mvc;BookingService service;Booking booking;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(WebOnly.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).defaultRequest(get("/").with(user("own@test.invalid").roles("PASSENGER"))).build();
        service=context.getBean(BookingService.class);LocalDate date=LocalDate.now().plusDays(1);
        booking=Booking.builder().id(1L).passenger(User.builder().email("own@test.invalid").build()).schedule(Schedule.builder().id(2L)
                .train(Train.builder().trainName("Demo Express").status(Train.TrainStatus.ON_TIME).build())
                .route(Route.builder().origin("Demo Origin").destination("Demo Destination").build()).dayOfWeek(date.getDayOfWeek()).departureTime(LocalTime.of(10,30)).arrivalTime(LocalTime.of(12,30)).build())
                .travelDate(date).seatClass(Booking.SeatClass.FIRST).numberOfSeats(1).seatNumbers("Car 01 / S03").totalAmount(new BigDecimal("1500.00"))
                .status(Booking.BookingStatus.PENDING).lockExpiresAt(LocalDateTime.now().plusMinutes(5)).passengerName("Demo Passenger")
                .documentType(Booking.DocumentType.NIC).passengerNic("200300901022").contactPhone("0771234567").build();
        when(service.getBookingById(1L)).thenReturn(booking);
    }
    @AfterEach void close(){context.close();}
    @Test void actualEditFormRendersSavedValuesCorrectActionAndOnlyEditableFields() throws Exception {
        String html=mvc.perform(get("/booking/1/details")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("action=\"/booking/1/details\""));assertTrue(html.contains("name=\"_csrf\""));
        assertTrue(html.contains("value=\"Demo Passenger\""));assertTrue(html.contains("value=\"200300901022\""));assertTrue(html.contains("Foreign Passport"));
        for(String field:new String[]{"totalAmount","seatNumbers","travelDate","scheduleId","lockExpiresAt","status"})assertFalse(html.contains("name=\""+field+"\""));
        Files.writeString(Path.of("target/booking-details-preview.html"),html);
    }
    @Test void editRequiresSignInCsrfAndOwnership() throws Exception {
        mvc.perform(get("/booking/1/details").with(anonymous())).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/booking/1/details").with(user("other@test.invalid"))).andExpect(status().isNotFound());
        mvc.perform(post("/booking/1/details")).andExpect(status().isForbidden());verify(service,never()).updatePassengerDetails(any(),any(),any());
    }
    @Test void expiredGetReturnsToCheckoutInsteadOfShowingAnEditableForm() throws Exception {
        booking.setLockExpiresAt(LocalDateTime.now().minusSeconds(1));
        mvc.perform(get("/booking/1/details")).andExpect(redirectedUrl("/booking/1/payment")).andExpect(flash().attributeExists("errorMessage"));
    }
    @Test void postedExtraAllocationFieldsCannotBeBoundToDetailsDto() throws Exception {
        mvc.perform(post("/booking/1/details").with(csrf()).param("passengerName","Corrected Passenger").param("documentType","NIC")
            .param("passengerNic","200300901022").param("contactPhone","0771234567").param("totalAmount","1").param("status","CONFIRMED")
            .param("schedule.id","999").param("lockExpiresAt","2099-01-01T00:00")).andExpect(redirectedUrl("/booking/1/payment"));
        var request=ArgumentCaptor.forClass(BookingDetailsRequest.class);verify(service).updatePassengerDetails(eq(1L),eq("own@test.invalid"),request.capture());
        assertEquals("Corrected Passenger",request.getValue().getPassengerName());assertEquals("0771234567",request.getValue().getContactPhone());
    }
    @Test void serverInvalidInputIsRetainedForCorrectionAndUnavailableSaveReturnsToPayment() throws Exception {
        when(service.updatePassengerDetails(any(),any(),any())).thenThrow(new IllegalArgumentException("Enter a valid phone."));
        var result=mvc.perform(post("/booking/1/details").with(csrf()).param("passengerName","Keep This Name").param("contactPhone","bad"))
            .andExpect(redirectedUrl("/booking/1/details")).andExpect(flash().attribute("detailsError","Enter a valid phone.")).andReturn();
        assertEquals("Keep This Name",((BookingDetailsRequest)result.getFlashMap().get("bookingDetails")).getPassengerName());
        doThrow(new IllegalStateException("Hold expired.")).when(service).updatePassengerDetails(any(),any(),any());
        mvc.perform(post("/booking/1/details").with(csrf())).andExpect(redirectedUrl("/booking/1/payment")).andExpect(flash().attribute("errorMessage","Hold expired."));
    }
    @Test void cancelDoesNotPretendToSucceedWhenPaymentWonTheRace() throws Exception {
        when(service.cancelPendingBooking(1L,"own@test.invalid")).thenThrow(new IllegalStateException("Already paid."));
        mvc.perform(post("/booking/1/cancel").with(csrf())).andExpect(redirectedUrl("/booking/1/payment")).andExpect(flash().attribute("errorMessage","Already paid."));
        doReturn(Booking.CancellationReason.PASSENGER).when(service).cancelPendingBooking(1L,"own@test.invalid");
        mvc.perform(post("/booking/1/cancel").with(csrf())).andExpect(redirectedUrl("/passenger/bookings")).andExpect(flash().attributeExists("successMessage"));
    }
}
