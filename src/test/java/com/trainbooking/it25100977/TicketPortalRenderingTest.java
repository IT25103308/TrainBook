package com.trainbooking.it25100977;

import com.trainbooking.config.SecurityConfig;
import com.trainbooking.exception.GlobalExceptionHandler;
import com.trainbooking.it25100977.controller.PaymentController;
import com.trainbooking.it25100977.model.*;
import com.trainbooking.it25100977.service.*;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.service.BookingService;
import org.junit.jupiter.api.*;
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

class TicketPortalRenderingTest {
    @Configuration @EnableWebMvc @Import({SecurityConfig.class,GlobalExceptionHandler.class})
    static class WebOnly {
        @Bean org.springframework.security.core.userdetails.UserDetailsService users(){return new org.springframework.security.provisioning.InMemoryUserDetailsManager();}
        @Bean BookingService bookings(){return mock(BookingService.class);}
        @Bean PaymentService payments(){return mock(PaymentService.class);}
        @Bean PaymentController controller(PaymentService p,BookingService b){return new PaymentController(p,b);}
        @Bean SpringTemplateEngine templates(){var r=new ClassLoaderTemplateResolver();r.setPrefix("templates/");r.setSuffix(".html");r.setTemplateMode("HTML");r.setCharacterEncoding("UTF-8");var e=new SpringTemplateEngine();e.setTemplateResolver(r);e.addDialect(new org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect());return e;}
        @Bean ThymeleafViewResolver views(SpringTemplateEngine e){var v=new ThymeleafViewResolver();v.setTemplateEngine(e);v.setCharacterEncoding("UTF-8");return v;}
    }
    AnnotationConfigWebApplicationContext context;MockMvc mvc;PaymentService service;Ticket ticket;
    @BeforeEach void setup(){context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(WebOnly.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).defaultRequest(get("/").with(user("own@test.invalid").roles("PASSENGER"))).build();service=context.getBean(PaymentService.class);
        Booking booking=Booking.builder().id(20L).passenger(User.builder().email("own@test.invalid").build()).passengerName("Demo Passenger")
            .documentType(Booking.DocumentType.FOREIGN_PASSPORT).passengerNic("AB1234567").contactPhone("0771234567").status(Booking.BookingStatus.CONFIRMED)
            .travelDate(LocalDate.now().plusDays(1)).seatClass(Booking.SeatClass.FIRST).numberOfSeats(2).seatNumbers("Car 01 / S03, Car 01 / S07")
            .schedule(Schedule.builder().train(Train.builder().trainName("Demo Express").build()).route(Route.builder().origin("Demo Origin").destination("Demo Destination").build())
                .departureTime(LocalTime.of(23,0)).arrivalTime(LocalTime.of(2,0)).arrivalDayOffset(1).build()).build();
        ticket=Ticket.builder().id(10L).ticketNumber("TKT-DEMO-20").booking(booking).emailDeliveryStatus("DISABLED")
            .payment(Payment.builder().status(Payment.PaymentStatus.COMPLETED).amount(new BigDecimal("3000.00")).build()).build();when(service.getTicketById(10L)).thenReturn(ticket);
    }
    @AfterEach void close(){context.close();}
    @Test void actualPortalShowsOwnedTicketCorrectDocumentDownloadActionsAndDemoEmailFeedback() throws Exception {
        String html=mvc.perform(get("/payment/ticket/10")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Passport: AB1234567"));assertTrue(html.contains("3,000.00"));assertTrue(html.contains("TKT-DEMO-20"));
        assertTrue(html.contains("/api/tickets/10/qr"));assertTrue(html.contains("/payment/receipt/10/download"));assertFalse(html.contains("api.qrserver.com"));
        assertTrue(html.contains("Email delivery is disabled for this local demo"));assertTrue(html.contains("own@test.invalid"));
        Files.writeString(Path.of("target/eticket-portal-preview.html"),html);
    }
    @Test void failedDownloadAndEmailShowUsefulFeedbackWithoutHidingTheIssuedTicket() throws Exception {
        ticket.setEmailDeliveryStatus("FAILED");String html=mvc.perform(get("/payment/ticket/10").param("downloadError","receipt"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("The document could not be generated"));assertTrue(html.contains("email could not be sent"));assertTrue(html.contains("Download PDF"));assertTrue(html.contains("TKT-DEMO-20"));
    }
    @Test void qrGenerationFailureIsAnUnavailableResourceRatherThanAFakeSuccessfulImage() throws Exception {
        when(service.generateQrCodeImage(10L)).thenThrow(new TicketDocumentException("Test QR failure",null));
        mvc.perform(get("/api/tickets/10/qr")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/payment/ticket/10").with(user("other@test.invalid"))).andExpect(status().isNotFound());
    }
}
