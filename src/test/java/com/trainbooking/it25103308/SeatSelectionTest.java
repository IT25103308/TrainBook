package com.trainbooking.it25103308;

import com.trainbooking.it25103308.model.Booking.SeatClass;
import com.trainbooking.it25103308.service.SeatSelection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SeatSelectionTest {
    @Test
    void normalizesNonAdjacentSelectedIdentitiesWithoutReassigningThem() {
        assertEquals(List.of("Car 02 / S03", "Car 02 / S24"),
                SeatSelection.parseRequested("Car 2 / S3, Car 02 / S24", SeatClass.SECOND, 30, 2));
    }

    @Test
    void expandsOlderStoredRangesForConflictChecks() {
        assertEquals(List.of("Car 01 / S01", "Car 01 / S02", "Car 01 / S03", "Car 01 / S07"),
                SeatSelection.parseStored("Car 01 / S1 - S3, Car 01 / S7", SeatClass.FIRST, 10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Car 01 / S01,", "Car 01 / S1, Car 01 / S01", "Car 01 / S3 - S2",
            "Car 01 / S0", "Car 01 / S11", "Car 02 / S01", "Car 01 / Seat A12, A13",
            "Car 01 / S9999999999999999999"})
    void rejectsMalformedOrUnmappableStoredLabels(String value) {
        assertThrows(IllegalArgumentException.class, () -> SeatSelection.parseStored(value, SeatClass.FIRST, 10));
    }
}
