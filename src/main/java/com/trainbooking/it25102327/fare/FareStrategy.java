package com.trainbooking.it25102327.fare;
import java.math.BigDecimal;
/** Strategy contract: a selected policy supplies the per-seat fare. */
public interface FareStrategy { String policy(); BigDecimal calculate(FareContext context); }
