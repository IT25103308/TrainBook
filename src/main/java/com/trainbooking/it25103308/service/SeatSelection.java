package com.trainbooking.it25103308.service;

import com.trainbooking.it25103308.model.Booking.SeatClass;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates seat identities and normalizes the older S1 - S2 storage format. */
public final class SeatSelection {
    private static final Pattern LABEL = Pattern.compile(
            "^Car\\s+(\\d+)\\s*/\\s*S(\\d+)(?:\\s*-\\s*S(\\d+))?$", Pattern.CASE_INSENSITIVE);

    private SeatSelection() {
    }

    public static List<String> parseRequested(String value, SeatClass seatClass, int capacity, int quantity) {
        List<String> seats = parse(value, seatClass, capacity, false);
        if (seats.size() != quantity) {
            throw new IllegalArgumentException("Choose exactly " + quantity + " different seats.");
        }
        return seats;
    }

    public static List<String> parseStored(String value, SeatClass seatClass, int capacity) {
        return parse(value, seatClass, capacity, true);
    }

    public static String label(SeatClass seatClass, int number) {
        return String.format(java.util.Locale.ROOT, "Car %02d / S%02d", seatClass == SeatClass.FIRST ? 1 : 2, number);
    }

    private static List<String> parse(String value, SeatClass seatClass, int capacity, boolean allowRanges) {
        if (value == null || value.isBlank() || value.length() > 1024) {
            throw new IllegalArgumentException("Please select valid seat numbers.");
        }
        Set<String> seats = new LinkedHashSet<>();
        for (String part : value.split(",", -1)) {
            Matcher matcher = LABEL.matcher(part.trim());
            if (!matcher.matches() || (!allowRanges && matcher.group(3) != null)) {
                throw new IllegalArgumentException("Please select individual seats from the seat map.");
            }
            int coach;
            int first;
            int last;
            try {
                coach = Integer.parseInt(matcher.group(1));
                first = Integer.parseInt(matcher.group(2));
                last = matcher.group(3) == null ? first : Integer.parseInt(matcher.group(3));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("A selected seat number is invalid.");
            }
            if (coach != (seatClass == SeatClass.FIRST ? 1 : 2)) {
                throw new IllegalArgumentException("The selected seats do not belong to this travel class.");
            }
            if (first < 1 || last < first || last > capacity) {
                throw new IllegalArgumentException("A selected seat is outside this class's capacity.");
            }
            for (int number = first; number <= last; number++) {
                if (!seats.add(label(seatClass, number))) {
                    throw new IllegalArgumentException("The same seat cannot be selected twice.");
                }
            }
        }
        return List.copyOf(seats);
    }
}
