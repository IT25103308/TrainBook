package com.trainbooking.it25103308;

import com.trainbooking.it25103308.dto.BookingRequest;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.service.BookingService;
import com.trainbooking.it25103308.service.PassengerDetails;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25101520.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PassengerDetailsTest {
    private BookingRequest valid() {
        return BookingRequest.builder().passengerName("Anfas M.S.").documentType("NIC")
                .passengerNic("200300901022").contactPhone("0771234567")
                .scheduleId(1L).travelDate(LocalDate.now().plusDays(1)).seatClass("FIRST").seatCount(1).build();
    }
    @ParameterizedTest
    @CsvSource({"NIC,200300901022,NIC", "NIC,855420159v,NIC", "NIC,855420159x,NIC",
        "SRI_LANKAN_PASSPORT,n1234567,Passport", "SRI_LANKAN_PASSPORT,OL1234567,Passport",
        "FOREIGN_PASSPORT,AB1234567,Passport", "FOREIGN_PASSPORT,012345678,Passport"})
    void validDocumentsAreNormalizedAndHaveTheCorrectLabel(String type, String number, String label) {
        var request = valid(); request.setDocumentType(type); request.setPassengerNic(" " + number + " ");
        var details = PassengerDetails.validate(request);
        assertEquals(number.toUpperCase(), details.documentNumber());
        assertEquals(label, Booking.builder().documentType(details.documentType()).passengerNic(details.documentNumber()).build().getDocumentLabel());
    }
    @ParameterizedTest
    @CsvSource({"NIC,20000000000000000000", "NIC,123456789", "NIC,1234567890", "NIC,123456789A", "NIC,2003 00901022",
        "SRI_LANKAN_PASSPORT,N123456", "SRI_LANKAN_PASSPORT,N12345678", "SRI_LANKAN_PASSPORT,12345678",
        "FOREIGN_PASSPORT,AB123", "FOREIGN_PASSPORT,AB-123456", "FOREIGN_PASSPORT,123456789012345678901"})
    void invalidDocumentsCannotCreateAnyHold(String type, String number) {
        var request = valid(); request.setDocumentType(type); request.setPassengerNic(number);
        var bookings = mock(BookingRepository.class); var schedules = mock(ScheduleRepository.class); var users = mock(UserRepository.class);
        assertThrows(IllegalArgumentException.class, () -> new BookingService(bookings, schedules, users, null).createBooking(request, 1L));
        verifyNoInteractions(bookings, schedules, users);
    }
    @ParameterizedTest
    @CsvSource({"0771234567,0771234567", "+94771234567,0771234567", "0094771234567,0771234567",
        "+94 77 123 4567,0771234567", "077-123-4567,0771234567", "0112345678,0112345678", "+94112345678,0112345678", "0741943329,0741943329"})
    void phoneFormatsNormalizeToTenDigits(String input, String expected) { assertEquals(expected, PassengerDetails.normalizePhone(input)); }
    @ParameterizedTest
    @ValueSource(strings={"", "077123456", "07712345678", "+940771234567", "+44771234567", "0991234567", "abc0771234567", "0771234567 ext 1", "++94771234567", "1234567890"})
    void badPhoneLengthsAndPrefixesAreRejected(String phone) { assertThrows(IllegalArgumentException.class, () -> PassengerDetails.normalizePhone(phone)); }
    @ParameterizedTest
    @ValueSource(strings={"AnfasMS", "A. B. Perera", "O'Connor", "Anne-Marie", "අනුර පෙරේරා", "அன்ஃபாஸ்"})
    void acceptsLocalScriptsAndNormalNamePunctuation(String name) { var request = valid(); request.setPassengerName(name); assertEquals(name, PassengerDetails.validate(request).name()); }
    @ParameterizedTest
    @ValueSource(strings={"", " ", "A", "12345", "---", "Anfas123", "<script>"})
    void invalidNamesAreRejected(String name) { var request = valid(); request.setPassengerName(name); assertThrows(IllegalArgumentException.class, () -> PassengerDetails.validate(request)); }
    @Test void missingDocumentChoiceCannotBeGuessedForANewBooking() { var request = valid(); request.setDocumentType(null); assertThrows(IllegalArgumentException.class, () -> PassengerDetails.validate(request)); }
    @Test void nameIsTrimmedWithoutReplacingPassengerIdentity() { var request = valid(); request.setPassengerName(" Anfas  M.S. "); assertEquals("Anfas M.S.", PassengerDetails.validate(request).name()); }
    @Test void legacyLabelIsConservative() {
        assertEquals("NIC", Booking.builder().passengerNic("200300901022").build().getDocumentLabel());
        assertEquals("Passport", Booking.builder().passengerNic("N1234567").build().getDocumentLabel());
        assertEquals("Document", Booking.builder().passengerNic("123456789").build().getDocumentLabel());
    }
}
