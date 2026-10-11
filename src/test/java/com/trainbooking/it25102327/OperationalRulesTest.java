package com.trainbooking.it25102327;
import com.trainbooking.it25102327.service.*;
import com.trainbooking.it25102327.dto.*;
import com.trainbooking.it25102327.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OperationalRulesTest {
    @Test void totalSeatsAreDerivedAndNamesAreCanonical() {
        var dto=TrainDto.builder().trainNumber(" tr-101 ").trainName(" Test  Express ").firstClassSeats(60).secondClassSeats(240).totalSeats(1).build();
        OperationalRules.train(dto);assertEquals(300,dto.getTotalSeats());assertEquals("TR-101",dto.getTrainNumber());assertEquals("Test Express",dto.getTrainName());
    }
    @ParameterizedTest @ValueSource(strings={"<script>","123456"," ","Name\\nOther","Name/route"})
    void malformedNamesAreRejected(String value) { assertThrows(OperationValidation.class,()->OperationalRules.name(value,"trainName",100)); }
    @ParameterizedTest @ValueSource(strings={"Platform 0","Platform 21","2.5","<b>1</b>",""})
    void platformBoundsAreEnforced(String value) { assertThrows(OperationValidation.class,()->OperationalRules.platform(value,"platform")); }
    @Test void stationNamesAndPlatformNumbersNormalizeWithoutMarkup() {
        assertEquals("කොළඹ",OperationalRules.name("කොළඹ","origin",100));assertEquals("Platform 2",OperationalRules.platform("2","platform"));
    }
    @Test void capacityDistanceFareAndIdentityBoundsAreChecked() {
        for(Integer invalid:Arrays.asList(null,-1,1001)) assertThrows(OperationValidation.class,()->OperationalRules.capacity(invalid,"firstClassSeats"));
        for(Integer invalid:Arrays.asList(null,0,2001)) assertThrows(OperationValidation.class,()->OperationalRules.distance(invalid));
        for(BigDecimal invalid:Arrays.asList(null,BigDecimal.ZERO,new BigDecimal("1.001"),new BigDecimal("1000000"))) assertThrows(OperationValidation.class,()->OperationalRules.fare(invalid,"firstClassFare",true));
        assertThrows(OperationValidation.class,()->OperationalRules.train(TrainDto.builder().trainNumber("ABC").trainName("Test").firstClassSeats(1).secondClassSeats(0).build()));
        assertThrows(OperationValidation.class,()->OperationalRules.train(TrainDto.builder().trainNumber("101").trainName("Test").firstClassSeats(0).secondClassSeats(0).build()));
    }
    @Test void overnightAndIntermediateStopDwellStayWithinTheJourney() {
        Schedule s=Schedule.builder().route(Route.builder().origin("Origin").destination("Destination").build()).departureTime(LocalTime.of(23,0)).arrivalTime(LocalTime.of(2,0)).arrivalDayOffset(1).build();
        var stops=OperationalRules.stops(List.of(new StopDto("Midnight stop","23:58","00:02",0,"2")),s);
        assertEquals(58,stops.get(0).getArrivalOffset());assertEquals(62,stops.get(0).getDepartureOffset());
        assertThrows(OperationValidation.class,()->OperationalRules.stops(List.of(new StopDto("Late","03:00","03:05",1,"1")),s));
        assertThrows(OperationValidation.class,()->OperationalRules.stops(List.of(new StopDto("Origin","23:20","23:25",0,"1")),s));
        assertThrows(OperationValidation.class,()->OperationalRules.duration(LocalTime.of(23,0),LocalTime.of(2,0),0,"arrivalTime"));
    }
}
