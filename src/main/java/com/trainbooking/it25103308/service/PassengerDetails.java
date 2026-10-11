package com.trainbooking.it25103308.service;

import com.trainbooking.it25103308.dto.BookingRequest;
import com.trainbooking.it25103308.model.Booking;
import java.util.Locale;

/** Format checks only; these do not verify ownership or issuance of an identity document. */
public record PassengerDetails(String name, Booking.DocumentType documentType, String documentNumber, String phone) {
    public static PassengerDetails validate(BookingRequest request) {
        String name = request.getPassengerName() == null ? "" : request.getPassengerName().strip();
        if (name.length() < 2 || name.length() > 100
                || !name.matches("[\\p{L}\\p{M} .'’-]+") || !name.matches(".*\\p{L}.*")) {
            throw new IllegalArgumentException("Enter the passenger's name as on their document, using 2–100 characters, letters, spaces, initials, apostrophes or hyphens.");
        }
        Booking.DocumentType type;
        try { type = Booking.DocumentType.valueOf(request.getDocumentType() == null ? "" : request.getDocumentType()); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Choose NIC, Sri Lankan passport or foreign passport."); }
        String number = request.getPassengerNic() == null ? "" : request.getPassengerNic().strip().toUpperCase(Locale.ROOT);
        String pattern = switch (type) {
            case NIC -> "(?:[0-9]{9}[VX]|[0-9]{12})";
            case SRI_LANKAN_PASSPORT -> "[A-Z]{1,2}[0-9]{7}";
            case FOREIGN_PASSPORT -> "[A-Z0-9]{6,20}";
        };
        if (!number.matches(pattern)) {
            throw new IllegalArgumentException(switch (type) {
                case NIC -> "NIC must contain 12 digits, or 9 digits followed by V or X.";
                case SRI_LANKAN_PASSPORT -> "Enter a Sri Lankan passport number with 1 or 2 letters followed by 7 digits, for example N1234567.";
                case FOREIGN_PASSPORT -> "Enter the foreign passport number exactly as printed: 6–20 letters or digits, without spaces or punctuation.";
            });
        }
        return new PassengerDetails(name.replaceAll(" +", " "), type, number, normalizePhone(request.getContactPhone()));
    }

    public static String normalizePhone(String value) {
        return com.trainbooking.validation.ContactRules.phone(value);
    }
}
