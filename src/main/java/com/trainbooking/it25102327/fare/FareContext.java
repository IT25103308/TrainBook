package com.trainbooking.it25102327.fare;
import java.math.BigDecimal;
import java.time.*;
public record FareContext(String policy, String seatClass, Integer distanceKm,
                          LocalTime departure, DayOfWeek day, BigDecimal fixedFare) { }
