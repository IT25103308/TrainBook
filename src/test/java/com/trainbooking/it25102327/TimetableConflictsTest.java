package com.trainbooking.it25102327;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.service.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TimetableConflictsTest {
    Schedule service(long id,long train,String origin,String destination,String departure,String arrival,DayOfWeek day,int offset) {
        return Schedule.builder().id(id).train(Train.builder().id(train).status(Train.TrainStatus.ON_TIME).build())
            .route(Route.builder().origin(origin).destination(destination).defaultPlatform("Platform 1").destinationPlatform("Platform 1").build())
            .departureTime(LocalTime.parse(departure)).arrivalTime(LocalTime.parse(arrival)).dayOfWeek(day).arrivalDayOffset(offset).isActive(true).build();
    }
    @Test void duplicateTrainOverlapCannotBeHiddenByUsingAnotherPlatformOrRoute() {
        var a=service(1,1,"Origin","Destination","08:30","11:00",DayOfWeek.MONDAY,0);
        var b=service(2,1,"Another","Elsewhere","10:30","13:00",DayOfWeek.MONDAY,0);
        assertTrue(TimetableConflicts.find(a,List.of(b)).contains("Train conflict"));
    }
    @Test void arrivalsAndDeparturesAtTheSameStationUseTheSamePlatformWindow() {
        var a=service(1,1,"Origin","Destination","08:30","11:00",DayOfWeek.MONDAY,0);
        var b=service(2,2,"Destination","Elsewhere","11:10","13:00",DayOfWeek.MONDAY,0);
        assertTrue(TimetableConflicts.find(a,List.of(b)).contains("Platform conflict"));
        b.setDepartureTime(LocalTime.of(11,20));assertNull(TimetableConflicts.find(a,List.of(b)));
    }
    @Test void overnightTrainAndPlatformConflictsCrossWeekdayBoundaries() {
        var a=service(1,1,"Origin","Destination","23:00","02:00",DayOfWeek.SUNDAY,1);
        var b=service(2,1,"Other","Elsewhere","01:00","04:00",DayOfWeek.MONDAY,0);
        assertNotNull(TimetableConflicts.find(a,List.of(b)));
        b.getTrain().setId(2L);b.getRoute().setOrigin("Destination");b.setDepartureTime(LocalTime.of(2,10));assertNotNull(TimetableConflicts.find(a,List.of(b)));
        b.setDepartureTime(LocalTime.of(2,20));assertNull(TimetableConflicts.find(a,List.of(b)));
    }
    @Test void intermediateStopDwellAndDatedAdditionalDepartureAreChecked() {
        var a=service(1,1,"Origin","Destination","08:30","11:00",DayOfWeek.MONDAY,0);
        var stop=new TimetableStop();stop.setStation("Intermediate");stop.setPlatform("Platform 2");stop.setArrivalOffset(60);stop.setDepartureOffset(70);a.getStops().add(stop);
        var b=service(2,2,"Intermediate","Elsewhere","09:50","13:00",DayOfWeek.MONDAY,0);b.setDeparturePlatform("Platform 2");assertNotNull(TimetableConflicts.find(a,List.of(b)));
        b.setDeparturePlatform("Platform 3");assertNull(TimetableConflicts.find(a,List.of(b)));
        var c=service(3,1,"Origin","Destination","23:00","03:00",DayOfWeek.MONDAY,1);
        LocalDate monday=LocalDate.now().plusWeeks(1).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        var extra=new ScheduleOverride();extra.setId(1L);extra.setServiceDate(monday.plusDays(1));extra.setKind(ScheduleOverride.Kind.TIMETABLE);extra.setDepartureTime(LocalTime.of(2,0));extra.setArrivalTime(LocalTime.of(5,0));extra.setArrivalDayOffset(0);c.getOverrides().add(extra);
        assertNotNull(TimetableConflicts.find(c,List.of(c)));
    }
}
