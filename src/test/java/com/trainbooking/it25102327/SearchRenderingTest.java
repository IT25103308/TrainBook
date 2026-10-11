package com.trainbooking.it25102327;

import com.trainbooking.it25102327.dto.TrainSearchResultDto;
import com.trainbooking.it25102327.model.Route;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import java.nio.file.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SearchRenderingTest {
    Map<String,Object> model() {
        var model=new HashMap<String,Object>();
        model.put("origin","Colombo Fort"); model.put("destination","Kandy"); model.put("seatClass","FIRST");
        model.put("today",LocalDate.now()); model.put("date",LocalDate.now().plusDays(1));
        model.put("stations",List.of("Colombo Fort","Kandy","New Admin Station"));
        model.put("configuredRoutes",List.of(Route.builder().origin("Colombo Fort").destination("Kandy").build()));
        return model;
    }
    String render(Map<String,Object> model) {
        var resolver=new ClassLoaderTemplateResolver(); resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8");
        var engine=new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var servlet=new MockServletContext(); var request=new MockHttpServletRequest(servlet); request.setRequestURI("/trains/search");
        var exchange=JakartaServletWebApplication.buildApplication(servlet).buildExchange(request,new MockHttpServletResponse());
        return engine.process("it25102327/search",new WebContext(exchange,Locale.ENGLISH,model));
    }
    @Test void landingPageRendersDynamicNativeFallbackAndEnhancementInsideLayout() throws Exception {
        String html=render(model());
        assertTrue(html.contains("New Admin Station")); assertTrue(html.contains("data-station-picker"));
        assertTrue(html.contains("/js/station-picker.js")); assertFalse(html.contains("stationList"));
        assertTrue(html.contains("Explore Available Routes")); assertFalse(html.contains("From LKR 600"));
        Files.writeString(Path.of("target/search-preview.html"),html);
    }
    @Test void resultsUseTheActualDateRemainingCountFareAndScheduleId() {
        var model=model(); model.put("trainResults",List.of(TrainSearchResultDto.builder().scheduleId(42L).trainName("Demo Service").trainNumber("42")
            .origin("Colombo Fort").destination("Kandy").departureTime(LocalTime.of(8,30)).arrivalTime(LocalTime.of(11,5))
            .availableFirstClass(58).availableSecondClass(239).firstClassFare(new BigDecimal("1500.00")).secondClassFare(new BigDecimal("800.00"))
            .selectedClass("FIRST").displayFare(new BigDecimal("1500.00")).status("SCHEDULED").build()));
        String html=render(model);
        assertTrue(html.contains("58 seats")); assertTrue(html.contains("LKR 1,500.00")); assertTrue(html.contains("scheduleId=42"));
        assertTrue(html.contains("date="+model.get("date"))); assertFalse(html.contains("Explore Available Routes"));
        assertTrue(html.contains("seatClass=FIRST"));
    }
    @Test void validationIsClearAndContainsNoStaleOrInventedResults() {
        var model=model(); model.put("searchError","Choose different origin and destination stations.");
        String html=render(model); assertTrue(html.contains("role=\"alert\"")); assertTrue(html.contains("Choose different"));
        assertFalse(html.contains("Book Now")); assertFalse(html.contains("Explore Available Routes"));
        assertTrue(html.contains("search-validation-error")); assertFalse(html.contains("alert alert-danger"));
    }
    @Test void noResultsIsAnHonestBookableServicesEmptyState() {
        var model=model(); model.put("trainResults",List.of()); String html=render(model);
        assertTrue(html.contains("No bookable services with available seats")); assertFalse(html.contains("Book Now"));
    }

    @Test void fullChosenClassShowsOnlyAnExplicitBookableAlternative() throws Exception {
        var model=model(); model.put("trainResults",List.of(TrainSearchResultDto.builder().scheduleId(42L).trainName("Demo Service").trainNumber("42")
            .origin("Colombo Fort").destination("Kandy").departureTime(LocalTime.of(8,30)).arrivalTime(LocalTime.of(11,5))
            .availableFirstClass(0).availableSecondClass(239).firstClassFare(new BigDecimal("1500.00")).secondClassFare(new BigDecimal("800.00"))
            .selectedClass("FIRST").displayFare(new BigDecimal("800.00")).status("SCHEDULED").build()));
        String html=render(model);
        assertTrue(html.contains("1st Class (AC) is unavailable")); assertTrue(html.contains("Choose 2nd Class Reserved"));
        assertTrue(html.contains("seatClass=SECOND")); assertFalse(html.contains("seatClass=FIRST")); assertFalse(html.contains("Book Now"));
        assertTrue(html.contains("LKR 800.00")); assertTrue(html.contains("Scheduled"));
        Files.writeString(Path.of("target/class-alternative-preview.html"),html);
    }
    @Test void allClassesLetsThePassengerChooseBeforeOpeningTheSeatMap() {
        var model=model(); model.put("seatClass",""); model.put("trainResults",List.of(TrainSearchResultDto.builder().scheduleId(42L).trainName("Demo Service")
            .availableFirstClass(60).availableSecondClass(240).firstClassFare(new BigDecimal("1500.00")).secondClassFare(new BigDecimal("800.00"))
            .selectedClass("").displayFare(new BigDecimal("800.00")).status("SCHEDULED").build()));
        String html=render(model); assertTrue(html.contains("seatClass=FIRST")); assertTrue(html.contains("seatClass=SECOND"));
        assertTrue(html.contains("Choose 1st Class")); assertTrue(html.contains("Choose 2nd Class")); assertFalse(html.contains("Book Now"));
    }

}
