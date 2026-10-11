package com.trainbooking.it25103308.dto;

import com.trainbooking.it25103308.model.Booking;
import lombok.Data;

/** Only editable contact/document values; allocation and financial fields are absent. */
@Data
public class BookingDetailsRequest {
    private String passengerName;
    private String documentType;
    private String passengerNic;
    private String contactPhone;

    public static BookingDetailsRequest from(Booking booking) {
        BookingDetailsRequest form = new BookingDetailsRequest();
        form.setPassengerName(booking.getPassengerName());
        form.setDocumentType(booking.getDocumentType() == null ? "" : booking.getDocumentType().name());
        form.setPassengerNic(booking.getPassengerNic());
        form.setContactPhone(booking.getContactPhone());
        return form;
    }

    public BookingRequest validatedFields() {
        return BookingRequest.builder().passengerName(passengerName).documentType(documentType)
                .passengerNic(passengerNic).contactPhone(contactPhone).build();
    }
}
