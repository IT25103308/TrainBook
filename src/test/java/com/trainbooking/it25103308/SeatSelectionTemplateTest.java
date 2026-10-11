package com.trainbooking.it25103308;

import com.trainbooking.it25103308.dto.BookingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Renders the real page and shared layout without booting the application. */
class SeatSelectionTemplateTest {
    @Test
    void rendersSeparateClassInventoriesAndReservationFeedback() throws Exception {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setRequestURI("/booking/seats");
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, new MockHttpServletResponse());
        Map<String, Object> model = new HashMap<>();
        model.put("schedule", Map.ofEntries(
                Map.entry("id", 10L), Map.entry("trainName", "Seat selection test"),
                Map.entry("trainType", "Express"), Map.entry("origin", "Colombo Fort"),
                Map.entry("destination", "Kandy"), Map.entry("departureTime", "08:00"),
                Map.entry("arrivalTime", "11:00"),Map.entry("arrivalDayOffset",0),Map.entry("departurePlatform","Platform 1"),Map.entry("arrivalPlatform","Platform 1"), Map.entry("firstClassFare", 1500),
                Map.entry("secondClassFare", 800), Map.entry("availableFirstClassSeats", 29),
                Map.entry("availableSecondClassSeats", 58)));
        model.put("date", LocalDate.now().plusDays(1));
        model.put("travelDateIso", LocalDate.now().plusDays(1).toString());
        model.put("bookingRequest", new BookingRequest(10L, LocalDate.now().plusDays(1), "SECOND", 2));
        model.put("firstClassCapacity", 30);
        model.put("secondClassCapacity", 60);
        model.put("firstClassAvailable", 29);
        model.put("secondClassAvailable", 58);
        model.put("bookedFirstSeats", List.of("Car 01 / S03"));
        model.put("bookedSecondSeats", List.of("Car 02 / S04", "Car 02 / S05"));
        model.put("bookingError", "Please choose another seat.");
        String html = engine.process("it25103308/seat-select", new WebContext(exchange, Locale.ENGLISH, model));
        assertTrue(html.contains("capacity: 30"));
        assertTrue(html.contains("capacity: 60"));
        assertTrue(html.contains("S03"));
        assertTrue(html.contains("S04"));
        assertTrue(html.contains("Please choose another seat."));
        assertTrue(html.contains("name=\"seatNumbers\""));
        assertTrue(html.contains("/js/seat-selection.js"));
        assertTrue(html.matches("(?s).*value=\"SECOND\"[^>]*checked=\"checked\".*"));
        assertFalse(html.matches("(?s).*value=\"FIRST\"[^>]*checked=\"checked\".*"));
        assertFalse(html.contains("secondsLeft = 600"));
        Files.writeString(Path.of("target", "seat-selection-preview.html"), html);
    }
}
