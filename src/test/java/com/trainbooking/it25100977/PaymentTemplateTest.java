package com.trainbooking.it25100977;

import com.trainbooking.it25103308.dto.CheckoutHold;
import com.trainbooking.it25103308.dto.CheckoutRecovery;
import com.trainbooking.it25103308.model.Booking;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Renders the real payment page through its shared layout without starting Spring Boot. */
class PaymentTemplateTest {
    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "EXPIRED", "EXPIRED_RELEASED", "CANCELLED_BY_PASSENGER", "CONFIRMED", "CANCELLED", "SERVICE_CANCELLED", "UNAVAILABLE"})
    void countdownScriptAndExactSeatsSurviveLayoutReplacement(String state) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        MockServletContext servlet = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest(servlet);
        request.setRequestURI("/booking/11/payment");
        var exchange = JakartaServletWebApplication.buildApplication(servlet)
                .buildExchange(request, new MockHttpServletResponse());
        Booking booking = Booking.builder().id(11L).travelDate(LocalDate.of(2026, 10, 6))
                .seatClass(Booking.SeatClass.FIRST).numberOfSeats(2).totalAmount(new BigDecimal("3000.00"))
                .seatNumbers("Car 01 / S03, Car 01 / S07")
                .status("CONFIRMED".equals(state) ? Booking.BookingStatus.CONFIRMED
                        : state.startsWith("CANCELLED") || "EXPIRED_RELEASED".equals(state)
                        ? Booking.BookingStatus.CANCELLED : Booking.BookingStatus.PENDING)
                .lockExpiresAt(LocalDateTime.now().plusMinutes(5)).build();
        CheckoutHold hold = new CheckoutHold(state, "PENDING".equals(state) ? 303000 : 0);
        boolean eligible = !"CONFIRMED".equals(state) && !"UNAVAILABLE".equals(state) && !"SERVICE_CANCELLED".equals(state);
        CheckoutRecovery recovery = new CheckoutRecovery(eligible, eligible ? 3600000 : 0,
                eligible ? "/booking/seats?scheduleId=2&date=2026-10-06" : null,
                eligible ? "Check current seat availability." : "This journey is unavailable. Please search another train.");
        String html = engine.process("it25100977/payment", new WebContext(exchange, Locale.ENGLISH,
                Map.of("booking", booking, "checkoutHold", hold, "checkoutRecovery", recovery)));
        assertTrue(html.contains("Car 01 / S03, Car 01 / S07"));
        assertTrue(html.contains("LKR 3,000.00"));
        assertTrue(html.contains("/js/payment-hold.js"), "Timer code must survive fragment replacement");
        assertTrue(html.contains("/js/payment-card.js"), "Card formatting must survive fragment replacement");
        assertTrue(html.contains("PaymentCard.mount({"));
        assertTrue(html.contains("currentYear: " + LocalDate.now().getYear()));
        assertTrue(html.contains("currentMonth: " + LocalDate.now().getMonthValue()));
        assertTrue(openingTag(html, "paymentForm").contains("no-loading"));
        for (String field : new String[]{"cardNumber", "cvv", "cardHolderName"}) {
            assertTrue(openingTag(html, field + "Error").contains("role=\"alert\""));
            assertTrue(openingTag(html, field + "Notice").contains("role=\"status\""));
            assertTrue(openingTag(html, field).contains(field + "Error"));
        }
        assertTrue(html.contains("value=\"" + (LocalDate.now().getYear() + 10) + "\""));
        assertTrue(html.contains("PaymentHold.mount("));
        assertTrue(html.contains("remainingMillis: " + hold.remainingMillis()));
        assertTrue(html.contains(hold.getDisplayTime()));
        assertFalse(html.contains("secondsLeft = 600"));
        assertTrue(html.contains("id=\"paymentFields\" disabled"), "Payment remains disabled until JS checks state");
        var cvvInput = java.util.regex.Pattern.compile("<input[^>]*id=\"cvv\"[^>]*>").matcher(html);
        assertTrue(cvvInput.find());
        assertTrue(cvvInput.group().contains("maxlength=\"3\""));
        assertTrue(cvvInput.group().contains("minlength=\"3\""));
        assertTrue(cvvInput.group().contains("pattern=\"[0-9]{3}\""));
        var cardInput = java.util.regex.Pattern.compile("<input[^>]*id=\"cardNumber\"[^>]*>").matcher(html);
        assertTrue(cardInput.find());
        assertTrue(cardInput.group().contains("maxlength=\"19\""));
        assertTrue(cardInput.group().contains("pattern=\"[0-9]{4}( ?[0-9]{4}){3}\""));
        assertFalse(html.contains("TLS Encrypted"), "The HTTP demo must not claim TLS encryption");
        assertTrue(html.contains(hold.getTitle()));
        assertTrue(html.contains(hold.getMessage()));
        assertEquals(!hold.isPayable(), openingTag(html, "paymentMethodCard").contains("hidden"));
        assertEquals(!hold.isRecoverable(), openingTag(html, "checkoutRecoveryActions").contains("hidden"));
        assertEquals(!eligible, openingTag(html, "selectSeatsAgain").contains("hidden"));
        assertTrue(html.contains("href=\"/booking/11/seats-again\""));
        assertTrue(html.contains("Search another train"));
        if (booking.getStatus() == Booking.BookingStatus.PENDING) {
            assertEquals(!hold.isPayable(), openingTag(html, "cancelReservationForm").contains("hidden"));
        } else {
            assertFalse(html.contains("id=\"cancelReservationForm\""));
        }
    }

    private String openingTag(String html, String id) {
        var element = java.util.regex.Pattern.compile("<[^>]*id=\"" + id + "\"[^>]*>").matcher(html);
        assertTrue(element.find(), "Missing element " + id);
        return element.group();
    }
}
