package com.trainbooking.it25102327.fare;
import java.math.*;
import java.time.*;
/** Existing project simulation; these multipliers are not an official railway tariff. */
public final class DemandFareStrategy implements FareStrategy {
    @Override public String policy() { return "DEMAND"; }
    @Override public BigDecimal calculate(FareContext c) {
        if(c.distanceKm()==null || c.distanceKm()<1 || c.distanceKm()>2000 || c.departure()==null || c.day()==null)
            throw new IllegalArgumentException("Distance, departure time and travel day are required for simulated demand pricing.");
        BigDecimal fare=BigDecimal.valueOf(c.distanceKm()).multiply(new BigDecimal("5.00"));
        if(c.seatClass().equals("FIRST")) fare=fare.multiply(new BigDecimal("1.8"));
        if(c.day()==DayOfWeek.SUNDAY || (c.day()==DayOfWeek.FRIDAY && c.departure().isAfter(LocalTime.of(15,0)))) fare=fare.multiply(new BigDecimal("1.25"));
        LocalTime time=c.departure();
        if((!time.isBefore(LocalTime.of(6,30)) && !time.isAfter(LocalTime.of(8,30))) || (!time.isBefore(LocalTime.of(16,30)) && !time.isAfter(LocalTime.of(18,30)))) fare=fare.multiply(new BigDecimal("1.15"));
        return fare.divide(BigDecimal.TEN,0,RoundingMode.HALF_UP).multiply(BigDecimal.TEN).setScale(2);
    }
}
