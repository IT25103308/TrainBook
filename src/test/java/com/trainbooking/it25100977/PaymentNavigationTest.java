package com.trainbooking.it25100977;

import com.trainbooking.it25100977.controller.PaymentController;
import com.trainbooking.it25100977.model.Payment;
import com.trainbooking.it25100977.model.Ticket;
import com.trainbooking.it25100977.service.PaymentService;
import com.trainbooking.it25103308.service.BookingService;
import com.trainbooking.it25103308.controller.BookingController;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.service.PassengerService;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentNavigationTest {
    private final PaymentService payments=mock(PaymentService.class);
    private final BookingService bookings=mock(BookingService.class);
    private MockMvc mvc;
    private Booking own;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.standaloneSetup(new PaymentController(payments,bookings),
            new BookingController(bookings,mock(ScheduleRepository.class),mock(PassengerService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        own=Booking.builder().id(24L).passenger(User.builder().email("own@test.invalid").build())
            .status(Booking.BookingStatus.PENDING).lockExpiresAt(LocalDateTime.now().plusMinutes(3)).build();
        when(bookings.getBookingById(24L)).thenReturn(own);
    }
    private Ticket ticket() {
        own.setStatus(Booking.BookingStatus.CONFIRMED);
        return Ticket.builder().id(10L).booking(own).payment(Payment.builder().status(Payment.PaymentStatus.COMPLETED).build()).build();
    }
    @Test void pendingBookingLinkResumesPaymentWithoutCreatingATicketOrNewHold() throws Exception {
        mvc.perform(get("/booking/ticket/24").principal(()->"own@test.invalid")).andExpect(redirectedUrl("/booking/24/payment"));
        verifyNoInteractions(payments); verify(bookings,never()).createBooking(any(),any());
    }
    @Test void expiredBookingLinkGoesToRecoveryAndDoesNotExtendTheDeadline() throws Exception {
        own.setLockExpiresAt(LocalDateTime.now().minusMinutes(1));
        mvc.perform(get("/booking/ticket/24").principal(()->"own@test.invalid")).andExpect(redirectedUrl("/booking/24/payment"));
        verify(bookings,never()).releaseLock(any()); verifyNoInteractions(payments);
    }
    @Test void confirmedBookingIdIsResolvedToItsDifferentTicketId() throws Exception {
        when(payments.findTicketForBooking(24L)).thenReturn(Optional.of(ticket()));
        mvc.perform(get("/booking/ticket/24").principal(()->"own@test.invalid")).andExpect(redirectedUrl("/payment/ticket/10"));
        verify(payments,never()).getTicketById(24L); verify(payments,never()).generateTicket(any());
    }
    @Test void missingTicketCannotFallbackToAnUnrelatedBooking() throws Exception {
        when(payments.getTicketById(10L)).thenThrow(new com.trainbooking.exception.ResourceNotFoundException("Ticket missing"));
        mvc.perform(get("/payment/ticket/10").principal(()->"own@test.invalid")).andExpect(status().isNotFound());
        verify(payments,never()).generateTicket(any());
    }
    @Test void anotherPassengerCannotViewPayOrCancelTheReservation() throws Exception {
        mvc.perform(get("/booking/24/payment").principal(()->"other@test.invalid")).andExpect(status().isNotFound());
        mvc.perform(post("/payment/process").param("bookingId","24").principal(()->"other@test.invalid")).andExpect(status().isNotFound());
        mvc.perform(post("/booking/24/cancel").principal(()->"other@test.invalid")).andExpect(status().isNotFound());
        verifyNoInteractions(payments); verify(bookings,never()).releaseLock(any());
    }
    @Test void anotherPassengerCannotReadOrDownloadTicketQrOrReceipt() throws Exception {
        when(payments.getTicketById(10L)).thenReturn(ticket());
        for(String path:new String[]{"/payment/ticket/10","/payment/ticket/10/download","/api/tickets/10/qr","/payment/receipt/10/download"}) {
            mvc.perform(get(path).principal(()->"other@test.invalid")).andExpect(status().isNotFound());
        }
        verify(payments,never()).generateTicketPdf(any()); verify(payments,never()).generateInvoicePdf(any()); verify(payments,never()).generateQrCodeImage(any());
    }
    @Test void ownPdfDownloadUsesTheActualTicketId() throws Exception {
        when(payments.getTicketById(10L)).thenReturn(ticket()); when(payments.generateTicketPdf(10L)).thenReturn("%PDF-test".getBytes());
        mvc.perform(get("/payment/ticket/10/download").principal(()->"own@test.invalid")).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        verify(payments).generateTicketPdf(10L); verify(payments,never()).generateTicketPdf(24L);
    }
    @Test void anIssuedTicketForACancelledReservationCannotBePresentedAsValid() throws Exception {
        Ticket ticket=ticket(); own.setStatus(Booking.BookingStatus.CANCELLED); when(payments.getTicketById(10L)).thenReturn(ticket);
        mvc.perform(get("/payment/ticket/10").principal(()->"own@test.invalid")).andExpect(status().isNotFound());
    }
    @Test void anonymousNavigationRequiresLogin() throws Exception {
        mvc.perform(get("/booking/24/payment")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/payment/ticket/10")).andExpect(redirectedUrl("/login")); verifyNoInteractions(payments);
    }
    @Test void aFailedDocumentDownloadReturnsToTheIssuedTicketWithClearFeedback() throws Exception {
        when(payments.getTicketById(10L)).thenReturn(ticket());
        when(payments.generateTicketPdf(10L)).thenThrow(new com.trainbooking.it25100977.service.TicketDocumentException("Test render failure",null));
        mvc.perform(get("/payment/ticket/10/download").principal(()->"own@test.invalid"))
            .andExpect(status().isSeeOther()).andExpect(header().string("Location","/payment/ticket/10?downloadError=ticket"));
        when(payments.generateInvoicePdf(10L)).thenThrow(new com.trainbooking.it25100977.service.TicketDocumentException("Test receipt failure",null));
        mvc.perform(get("/payment/receipt/10/download").principal(()->"own@test.invalid"))
            .andExpect(status().isSeeOther()).andExpect(header().string("Location","/payment/ticket/10?downloadError=receipt"));
    }
    @Test void checkoutCallsTheSingleAtomicPaymentAndIssuanceOperation() throws Exception {
        when(payments.payAndIssue(eq(24L),any())).thenReturn(ticket());
        mvc.perform(post("/payment/process").principal(()->"own@test.invalid").param("bookingId","24"))
            .andExpect(redirectedUrl("/payment/ticket/10"));
        verify(payments).payAndIssue(eq(24L),any());verify(payments,never()).processPayment(any(),any());verify(payments,never()).generateTicket(any());
    }
}
