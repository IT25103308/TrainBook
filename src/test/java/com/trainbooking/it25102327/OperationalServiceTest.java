package com.trainbooking.it25102327;
import com.trainbooking.it25102327.dto.*;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.repository.*;
import com.trainbooking.it25102327.service.*;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import org.junit.jupiter.api.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OperationalServiceTest {
    ScheduleRepository schedules;TrainRepository trains;RouteRepository routes;BookingRepository bookings;CatalogWriteLock lock;ScheduleService service;
    Train train;Route route;Schedule schedule;LocalDate date;
    @BeforeEach void setup() {
        schedules=mock(ScheduleRepository.class);trains=mock(TrainRepository.class);routes=mock(RouteRepository.class);bookings=mock(BookingRepository.class);lock=mock(CatalogWriteLock.class);
        service=new ScheduleService(schedules,trains,routes,bookings,lock);date=LocalDate.now().plusDays(2);
        train=Train.builder().id(1L).trainNumber("101").trainName("Test train").status(Train.TrainStatus.ON_TIME).firstClassSeats(60).secondClassSeats(240).build();
        route=Route.builder().id(1L).origin("Origin").destination("Destination").distanceKm(100).defaultPlatform("Platform 1").destinationPlatform("Platform 2").build();
        schedule=Schedule.builder().id(1L).train(train).route(route).dayOfWeek(date.getDayOfWeek()).departureTime(LocalTime.of(10,0)).arrivalTime(LocalTime.NOON)
            .arrivalDayOffset(0).firstClassFare(new BigDecimal("900.00")).secondClassFare(new BigDecimal("500.00")).build();
        when(schedules.findByIdForUpdate(1L)).thenReturn(Optional.of(schedule));when(schedules.findById(1L)).thenReturn(Optional.of(schedule));
        when(trains.findById(1L)).thenReturn(Optional.of(train));when(routes.findById(1L)).thenReturn(Optional.of(route));
        when(schedules.save(any())).thenAnswer(i->i.getArgument(0));when(schedules.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
    }
    OverrideDto override(String kind) { var dto=new OverrideDto();dto.setKind(kind);dto.setServiceDate(date.toString());dto.setLabel("Holiday service");dto.setDepartureTime("13:00");dto.setArrivalTime("15:00");return dto; }
    @Test void anyBookingHistoryPreventsTimetableDeletionWithoutCascade() {
        when(bookings.findByScheduleId(1L)).thenReturn(List.of(Booking.builder().status(Booking.BookingStatus.CANCELLED).build()));
        assertThrows(IllegalArgumentException.class,()->service.deleteSchedule(1L));verify(schedules,never()).delete(any());verify(bookings,never()).deleteByScheduleId(any());
    }
    @Test void unusedTimetableDeletesWithoutTouchingPassengerRecords() {
        service.deleteSchedule(1L);verify(schedules).delete(schedule);verify(bookings,never()).deleteByScheduleId(any());verify(lock).acquire();
    }
    @Test void referencedRouteDeletionIsRejectedInsteadOfDeletingItsTimetables() {
        when(schedules.findAll()).thenReturn(List.of(schedule));assertThrows(IllegalArgumentException.class,()->service.deleteRoute(1L));verify(routes,never()).delete(any());verify(schedules,never()).delete(any());
    }
    @Test void duplicateAndSameStationRoutesCannotBeStored() {
        when(routes.findAll()).thenReturn(List.of(route));
        assertThrows(OperationValidation.class,()->service.createRoute(" origin ","destination",100,"2"));
        assertThrows(OperationValidation.class,()->service.createRoute("Origin","origin",100,"1"));verify(routes,never()).save(any());
    }
    @Test void paidAndCancelledHistoryPreventsADatedTimetableReplacement() {
        for(var status:List.of(Booking.BookingStatus.CONFIRMED,Booking.BookingStatus.CANCELLED)) {
            when(bookings.findByScheduleId(1L)).thenReturn(List.of(Booking.builder().travelDate(date).status(status).build()));
            assertThrows(IllegalArgumentException.class,()->service.saveOverride(1L,null,override("TIMETABLE")));
        }
        assertTrue(schedule.getOverrides().isEmpty());verify(schedules,never()).saveAndFlush(any());
    }
    @Test void maintenanceWarnsKeepsPaidHistoryAndBlocksOnlyItsDate() {
        when(bookings.findByScheduleId(1L)).thenReturn(List.of(Booking.builder().travelDate(date).status(Booking.BookingStatus.CONFIRMED).build()));
        String result=service.saveOverride(1L,null,override("MAINTENANCE"));assertTrue(result.startsWith("WARNING"));
        assertFalse(ServicePlan.resolve(schedule,date).availableAt(LocalDateTime.now()));assertFalse(ServicePlan.resolve(schedule,date.plusWeeks(1)).maintenance());
        verify(bookings,never()).save(any());verify(bookings,never()).deleteByScheduleId(any());
    }
    @Test void aHolidayOverrideAddsAnExactDateWithoutChangingTheWeeklyTimetable() {
        schedule.setDayOfWeek(date.getDayOfWeek().plus(1));service.saveOverride(1L,null,override("TIMETABLE"));
        var effective=ServicePlan.resolve(schedule,date);assertTrue(effective.runs());assertEquals(LocalTime.of(13,0),effective.departure());
        assertFalse(ServicePlan.resolve(schedule,date.plusWeeks(1)).runs());assertEquals(LocalTime.of(10,0),schedule.getDepartureTime());
    }
    @Test void loweringCapacityCannotRemoveAHeldNonAdjacentSeat() {
        when(schedules.findByTrainId(1L)).thenReturn(List.of(schedule));
        when(bookings.findByScheduleId(1L)).thenReturn(List.of(Booking.builder().travelDate(date).seatClass(Booking.SeatClass.FIRST).numberOfSeats(1).seatNumbers("Car 01 / S60").status(Booking.BookingStatus.PENDING).build()));
        var dto=TrainDto.builder().trainNumber("101").trainName("Test train").firstClassSeats(59).secondClassSeats(240).build();
        assertThrows(OperationValidation.class,()->service.guardTrainEdit(train,dto));dto.setFirstClassSeats(60);assertDoesNotThrow(()->service.guardTrainEdit(train,dto));
    }
}
