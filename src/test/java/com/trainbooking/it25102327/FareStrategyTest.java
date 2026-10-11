package com.trainbooking.it25102327;
import com.trainbooking.it25102327.fare.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class FareStrategyTest {
    @ParameterizedTest @CsvSource({"SECOND,11:00,WEDNESDAY,500.00","FIRST,11:00,WEDNESDAY,900.00","SECOND,07:30,WEDNESDAY,580.00","FIRST,07:30,WEDNESDAY,1040.00","SECOND,15:00,FRIDAY,500.00","SECOND,15:01,FRIDAY,630.00","FIRST,17:00,SUNDAY,1290.00"})
    void demandStrategyPreservesTheProjectTariffAndRounding(String seat,String time,String day,String expected) {
        assertEquals(new BigDecimal(expected),FareCalculator.standard().quote(new FareContext("DEMAND",seat,100,LocalTime.parse(time),DayOfWeek.valueOf(day),null)));
    }
    @Test void storedPolicySwitchesAlgorithmsThroughTheSameContext() {
        var calculator=FareCalculator.standard();
        assertEquals(new BigDecimal("1234.50"),calculator.quote(new FareContext("FIXED","FIRST",100,LocalTime.NOON,DayOfWeek.SUNDAY,new BigDecimal("1234.50"))));
        assertEquals(new BigDecimal("1130.00"),calculator.quote(new FareContext("DEMAND","FIRST",100,LocalTime.NOON,DayOfWeek.SUNDAY,new BigDecimal("1234.50"))));
    }
    @Test void unsupportedPolicyClassAndMissingDataFailBeforeACharge() {
        var calculator=FareCalculator.standard();
        assertThrows(IllegalArgumentException.class,()->calculator.quote(new FareContext("UNKNOWN","FIRST",100,LocalTime.NOON,DayOfWeek.MONDAY,BigDecimal.TEN)));
        assertThrows(IllegalArgumentException.class,()->calculator.quote(new FareContext("FIXED","THIRD",100,LocalTime.NOON,DayOfWeek.MONDAY,BigDecimal.TEN)));
        assertThrows(IllegalArgumentException.class,()->calculator.quote(new FareContext("DEMAND","FIRST",null,LocalTime.NOON,DayOfWeek.MONDAY,null)));
        assertThrows(IllegalArgumentException.class,()->calculator.quote(new FareContext("FIXED","FIRST",100,LocalTime.NOON,DayOfWeek.MONDAY,new BigDecimal("-1"))));
        assertThrows(IllegalArgumentException.class,()->new FareCalculator(List.of(new FixedFareStrategy(),new FixedFareStrategy())));
    }
}
