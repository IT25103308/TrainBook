package com.trainbooking.it25102327.fare;
import java.math.BigDecimal;
import java.util.*;
/** Strategy context uses composition; the stored schedule policy chooses an algorithm at runtime. */
public final class FareCalculator {
    private final Map<String,FareStrategy> strategies;
    public FareCalculator(List<FareStrategy> rules) {
        Map<String,FareStrategy> collected=new HashMap<>();
        for(FareStrategy rule:rules) if(collected.putIfAbsent(rule.policy(),rule)!=null) throw new IllegalArgumentException("Duplicate fare policy.");
        strategies=Map.copyOf(collected);
    }
    public static FareCalculator standard() { return new FareCalculator(List.of(new FixedFareStrategy(),new DemandFareStrategy())); }
    public BigDecimal quote(FareContext context) {
        if(context==null || !Set.of("FIRST","SECOND").contains(context.seatClass())) throw new IllegalArgumentException("Choose a supported seat class.");
        FareStrategy rule=strategies.get(context.policy()==null ? "FIXED" : context.policy());
        if(rule==null) throw new IllegalArgumentException("Choose Fixed fares or Simulated demand pricing.");
        return rule.calculate(context);
    }
}
