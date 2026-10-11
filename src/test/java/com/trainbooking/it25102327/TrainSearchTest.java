package com.trainbooking.it25102327;

import com.trainbooking.it25102327.controller.TrainController;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.repository.*;
import com.trainbooking.it25102327.service.*;
import com.trainbooking.it25102925.service.NotificationService;
import com.trainbooking.it25103308.service.BookingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ui.ExtendedModelMap;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainSearchTest {
    RouteRepository routes; ScheduleRepository schedules; BookingService bookings;
    TrainService service; Schedule sample; LocalDate date;
    @BeforeEach void setup() {
        routes=mock(RouteRepository.class); schedules=mock(ScheduleRepository.class); bookings=mock(BookingService.class);
        service=new TrainService(mock(TrainRepository.class),routes,schedules,mock(ScheduleService.class),bookings);
        date=LocalDate.now().plusDays(1);
        Route route=Route.builder().id(1L).origin("Colombo Fort").destination("Kandy").build();
        when(routes.findAll()).thenReturn(List.of(route));
        Train train=Train.builder().id(1L).trainName("Test Train").trainNumber("1015").firstClassSeats(60).secondClassSeats(240).status(Train.TrainStatus.ON_TIME).build();
        sample=Schedule.builder().id(1L).route(route).train(train).dayOfWeek(date.getDayOfWeek()).departureTime(LocalTime.of(8,30)).arrivalTime(LocalTime.of(11,5))
            .firstClassFare(new BigDecimal("1500.00")).secondClassFare(new BigDecimal("800.00")).isActive(true).build();
    }
    void available(int first,int second) {
        when(schedules.findMatchingSchedulesOnDate("Colombo Fort","Kandy",date.getDayOfWeek(),date)).thenReturn(List.of(sample));
        when(bookings.getAvailableSeats(1L,date,"FIRST")).thenReturn(first);
        when(bookings.getAvailableSeats(1L,date,"SECOND")).thenReturn(second);
    }
    @Test void choicesUseAllSavedEndpointsAndDeduplicateWithoutCaching() {
        when(routes.findAll()).thenReturn(List.of(sample.getRoute(),Route.builder().origin(" kandy ").destination("New Station").build()));
        assertEquals(List.of("Colombo Fort","Kandy","New Station"),service.getStations());
        when(routes.findAll()).thenReturn(List.of(Route.builder().origin("Galle").destination("Matara").build()));
        assertEquals(List.of("Galle","Matara"),service.getStations());
    }
    @ParameterizedTest @CsvSource(value={"NULL,Kandy", "Colombo Fort,NULL", "NULL,NULL", "Colombo,Kandy", "Colombo Fort,Bogus", "Kandy,kandy"},nullValues="NULL")
    void invalidStationsAreRejectedBeforeScheduleQuery(String origin,String destination) {
        assertThrows(IllegalArgumentException.class,()->service.searchTrains(origin,destination,date,""));
        verifyNoInteractions(schedules,bookings);
    }
    @Test void pastDateAndUnknownClassAreRejected() {
        assertThrows(IllegalArgumentException.class,()->service.searchTrains("Colombo Fort","Kandy",LocalDate.now().minusDays(1),""));
        assertThrows(IllegalArgumentException.class,()->service.searchTrains("Colombo Fort","Kandy",date,"THIRD"));
        verifyNoInteractions(schedules,bookings);
    }
    @Test void stationNamesAreCanonicalAndRemainingCountsAreUsed() {
        available(58,239);
        var result=service.searchTrains(" colombo fort ","KANDY",date,"").get(0);
        assertEquals(58,result.getAvailableFirstClassSeats()); assertEquals(239,result.getAvailableSecondClassSeats());
        assertEquals("LKR 800.00",result.getFormattedFare());
    }
    @Test void noResultsDoesNotSubstituteADifferentWeekday() {
        assertTrue(service.searchTrains("Colombo Fort","Kandy",date,"").isEmpty());
        verify(schedules,never()).findMatchingSchedulesAnyDay(anyString(),anyString()); verify(schedules,never()).findAll();
    }
    @Test void fullyHeldSelectedClassKeepsTheTrainWithAnExplicitAlternativeAndCorrectFare() {
        available(0,239);
        var alternative=service.searchTrains("Colombo Fort","Kandy",date,"FIRST").get(0);
        assertFalse(alternative.isSelectedClassAvailable()); assertTrue(alternative.isSecondClassBookable());
        assertEquals("FIRST",alternative.getSelectedClass()); assertEquals("LKR 800.00",alternative.getFormattedFare());
        assertEquals("LKR 800.00",service.searchTrains("Colombo Fort","Kandy",date,"SECOND").get(0).getFormattedFare());
        when(bookings.getAvailableSeats(1L,date,"FIRST")).thenReturn(58);
        assertEquals("LKR 1,500.00",service.searchTrains("Colombo Fort","Kandy",date,"FIRST").get(0).getFormattedFare());
    }
    @Test void cheapestDisplayedFareMustHaveAvailableSeats() {
        available(58,0);
        assertEquals("LKR 1,500.00",service.searchTrains("Colombo Fort","Kandy",date,"").get(0).getFormattedFare());
        when(bookings.getAvailableSeats(1L,date,"FIRST")).thenReturn(0);
        assertTrue(service.searchTrains("Colombo Fort","Kandy",date,"").isEmpty());
    }
    @ParameterizedTest @CsvSource({"CANCELLED","MAINTENANCE","INACTIVE"})
    void closedTrainStatesCannotBeBooked(String status) {
        when(schedules.findMatchingSchedulesOnDate("Colombo Fort","Kandy",date.getDayOfWeek(),date)).thenReturn(List.of(sample));
        sample.getTrain().setStatus(Train.TrainStatus.valueOf(status));
        assertTrue(service.searchTrains("Colombo Fort","Kandy",date,"").isEmpty()); verifyNoInteractions(bookings);
    }
    @Test void maintenanceInactiveWrongDayAndDepartedServicesAreExcluded() {
        when(schedules.findMatchingSchedulesOnDate("Colombo Fort","Kandy",date.getDayOfWeek(),date)).thenReturn(List.of(sample));
        sample.setIsMaintenanceBlocked(true); assertTrue(service.searchTrains("Colombo Fort","Kandy",date,"").isEmpty());
        sample.setIsMaintenanceBlocked(false); sample.setIsActive(false); assertTrue(service.searchTrains("Colombo Fort","Kandy",date,"").isEmpty());
        sample.setIsActive(true); sample.setDayOfWeek(date.getDayOfWeek().plus(1)); assertTrue(service.searchTrains("Colombo Fort","Kandy",date,"").isEmpty());
        date=LocalDate.now(); sample.setDayOfWeek(date.getDayOfWeek()); sample.setDepartureTime(LocalTime.MIDNIGHT);
        when(schedules.findMatchingSchedulesOnDate("Colombo Fort","Kandy",date.getDayOfWeek(),date)).thenReturn(List.of(sample));
        assertTrue(service.searchTrains("Colombo Fort","Kandy",date,"").isEmpty()); verifyNoInteractions(bookings);
    }
    @Test void invalidFareCannotProduceABookableClass() {
        available(58,240); sample.setFirstClassFare(new BigDecimal("-1"));
        assertFalse(service.searchTrains("Colombo Fort","Kandy",date,"FIRST").get(0).isSelectedClassAvailable());
        assertEquals(0,service.searchTrains("Colombo Fort","Kandy",date,"").get(0).getAvailableFirstClass());
    }
    @Test void controllerShowsValidationInTheSearchPageAndRetainsDate() {
        var scheduleService=mock(ScheduleService.class);
        var controller=new TrainController(service,scheduleService,mock(NotificationService.class));
        var model=new ExtendedModelMap();
        assertEquals("it25102327/search",controller.searchTrains("","Kandy",date.toString(),"",model));
        assertTrue(model.containsAttribute("searchError")); assertFalse(model.containsAttribute("trainResults"));
        assertEquals(date,model.get("date"));
    }
    @ParameterizedTest @CsvSource({"not-a-date", "2026-99-31", "2026-02-30"})
    void malformedDateShowsAUsefulErrorRatherThanACrash(String dateText) {
        var controller=new TrainController(service,mock(ScheduleService.class),mock(NotificationService.class));
        var model=new ExtendedModelMap(); controller.searchTrains("Colombo Fort","Kandy",dateText,"",model);
        assertEquals("Choose a valid travel date.",model.get("searchError")); verifyNoInteractions(schedules);
    }
    @Test void landingPageDoesNotPretendAnEmptySearchWasSuccessful() {
        var controller=new TrainController(service,mock(ScheduleService.class),mock(NotificationService.class));
        var model=new ExtendedModelMap(); controller.searchTrains(null,null,null,null,model);
        assertFalse(model.containsAttribute("trainResults")); assertFalse(model.containsAttribute("searchError"));
        assertEquals(List.of("Colombo Fort","Kandy"),model.get("stations"));
    }

    @Test void futureDeparturesDoNotInheritTodaysLiveDelayLabel() {
        available(60,240); sample.getTrain().setStatus(Train.TrainStatus.DELAYED);
        assertEquals("SCHEDULED",service.searchTrains("Colombo Fort","Kandy",date,"FIRST").get(0).getStatus());
    }
    @Test void todaysDelayedServiceStillHasLiveStatusBeforeItsScheduledDeparture() {
        date=LocalDate.now(); sample.setDayOfWeek(date.getDayOfWeek()); sample.setDepartureTime(LocalTime.MAX);
        sample.getTrain().setStatus(Train.TrainStatus.DELAYED); available(60,240);
        assertEquals("DELAYED",service.searchTrains("Colombo Fort","Kandy",date,"FIRST").get(0).getStatus());
    }
    @Test void soldOutSecondClassOffersFirstWithoutSilentlyChangingTheSelection() {
        available(60,0); var result=service.searchTrains("Colombo Fort","Kandy",date,"SECOND").get(0);
        assertEquals("SECOND",result.getSelectedClass()); assertFalse(result.isSelectedClassAvailable());
        assertTrue(result.isFirstClassBookable()); assertFalse(result.isSecondClassBookable());
        assertEquals("LKR 1,500.00",result.getFormattedFare());
    }

}
