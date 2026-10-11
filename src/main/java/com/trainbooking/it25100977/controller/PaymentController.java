package com.trainbooking.it25100977.controller;

import com.trainbooking.it25100977.dto.PaymentRequest;
import com.trainbooking.it25100977.model.Payment;
import com.trainbooking.it25100977.model.Ticket;
import com.trainbooking.it25100977.service.PaymentService;
import com.trainbooking.it25103308.service.BookingService;
import com.trainbooking.it25103308.service.BookingAccess;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.exception.ResourceNotFoundException;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Controller handling payment submission, electronic ticket rendering, and PDF downloading.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final BookingService bookingService;

    @InitBinder("paymentRequest")
    void bindPayment(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("bookingId","cardNumber","cardHolderName","expiryMonth","expiryYear","cvv");
    }

    /**
     * Processes mock payment and redirects to issued e-ticket view.
     *
     * @param paymentRequest payment form data
     * @return redirect to ticket view
     */
    @PostMapping("/payment/process")
    public String processPayment(
            @ModelAttribute("paymentRequest") PaymentRequest paymentRequest,
            Principal principal,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes
    ) {
        if (principal == null) return "redirect:/login";
        if (paymentRequest.getBookingId() == null) throw new ResourceNotFoundException("Reservation not found.");
        BookingAccess.requireOwner(bookingService.getBookingById(paymentRequest.getBookingId()), principal);
        log.info("Received payment process request for booking ID: {}", paymentRequest.getBookingId());
        try {
            Ticket ticket = paymentService.payAndIssue(paymentRequest.getBookingId(), paymentRequest);
            return "redirect:/payment/ticket/" + ticket.getId();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Payment failed for booking #{}: {}", paymentRequest.getBookingId(), ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/booking/" + paymentRequest.getBookingId() + "/payment?declined=true";
        }
    }

    /**
     * Displays issued e-ticket details and QR code view.
     * Supports both /payment/ticket/{id} and /booking/ticket/{id}.
     *
     * @param id ticket or booking ID
     * @param model UI model
     * @return 'it25100977/eticket' template name
     */
    @GetMapping("/payment/ticket/{id}")
    public String showTicket(@PathVariable("id") Long id, Principal principal, Model model) {
        if (principal == null) return "redirect:/login";
        log.debug("Displaying ticket view for ID: {}", id);
        Ticket ticket = ownedTicket(id, principal);
        model.addAttribute("ticket", ticket);
        var plan=com.trainbooking.it25102327.service.ServicePlan.resolve(ticket.getBooking().getSchedule(),ticket.getBooking().getTravelDate());
        model.addAttribute("departureStatus",plan.status());
        model.addAttribute("departureDelay",plan.delayMinutes());
        return "it25100977/eticket";
    }

    /** This route accepts a booking ID; ticket routes always accept an actual ticket ID. */
    @GetMapping("/booking/ticket/{id}")
    public String openReservation(@PathVariable("id") Long id, Principal principal,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        Booking booking = bookingService.getBookingById(id);
        BookingAccess.requireOwner(booking, principal);
        if (booking.getStatus() != Booking.BookingStatus.CONFIRMED) return "redirect:/booking/" + id + "/payment";
        return paymentService.findTicketForBooking(id).map(ticket -> "redirect:/payment/ticket/" + ticket.getId())
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "The ticket for this paid reservation is unavailable. Please contact support.");
                    return "redirect:/passenger/bookings";
                });
    }

    private Ticket ownedTicket(Long id, Principal principal) {
        Ticket ticket = paymentService.getTicketById(id);
        BookingAccess.requireOwner(ticket.getBooking(), principal);
        if (ticket.getBooking().getStatus() != Booking.BookingStatus.CONFIRMED
                || ticket.getPayment() == null || ticket.getPayment().getStatus() != Payment.PaymentStatus.COMPLETED) {
            throw new ResourceNotFoundException("Issued ticket not found.");
        }
        return ticket;
    }

    /**
     * Serves live PNG barcode image for the ticket QR code.
     *
     * @param id ticket ID
     * @return PNG image bytes
     */
    @GetMapping(value = "/api/tickets/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] getTicketQrCode(@PathVariable("id") Long id, Principal principal) {
        ownedTicket(id, principal);
        return paymentService.generateQrCodeImage(id);
    }

    /**
     * Downloads ticket as a generated PDF document.
     *
     * @param id ticket ID
     * @return {@link ResponseEntity} containing binary PDF stream
     */
    @GetMapping("/payment/ticket/{id}/download")
    public ResponseEntity<byte[]> downloadTicketPdf(@PathVariable("id") Long id, Principal principal) {
        ownedTicket(id, principal);
        log.info("Downloading PDF for ticket ID: {}", id);
        byte[] pdfBytes;
        try { pdfBytes = paymentService.generateTicketPdf(id); }
        catch (com.trainbooking.it25100977.service.TicketDocumentException failure) {
            return ResponseEntity.status(303).header(HttpHeaders.LOCATION,"/payment/ticket/"+id+"?downloadError=ticket").build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ticket-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    /**
     * Downloads official tax invoice and payment receipt as PDF.
     *
     * @param id ticket ID
     * @return PDF stream
     */
    @GetMapping({"/payment/receipt/{id}/download", "/payment/invoice/{id}/download"})
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable("id") Long id, Principal principal) {
        ownedTicket(id, principal);
        log.info("Downloading Tax Invoice PDF for ticket ID: {}", id);
        byte[] pdfBytes;
        try { pdfBytes = paymentService.generateInvoicePdf(id); }
        catch (com.trainbooking.it25100977.service.TicketDocumentException failure) {
            return ResponseEntity.status(303).header(HttpHeaders.LOCATION,"/payment/ticket/"+id+"?downloadError=receipt").build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=receipt-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
