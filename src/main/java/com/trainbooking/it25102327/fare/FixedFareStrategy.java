package com.trainbooking.it25102327.fare;
import java.math.BigDecimal;
public final class FixedFareStrategy implements FareStrategy {
    @Override public String policy() { return "FIXED"; }
    @Override public BigDecimal calculate(FareContext context) {
        BigDecimal fare=context.fixedFare();
        if (fare==null || fare.signum()<=0 || fare.stripTrailingZeros().scale()>2 || fare.compareTo(new BigDecimal("999999.99"))>0)
            throw new IllegalArgumentException("Enter a positive fare with at most two decimal places.");
        return fare.setScale(2);
    }
}
