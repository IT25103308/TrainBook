package com.trainbooking.it25100977.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.canvas.draw.DashedLine;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25100977.dto.PaymentRequest;
import com.trainbooking.it25100977.model.Payment;
import com.trainbooking.it25100977.model.Ticket;
import com.trainbooking.it25100977.repository.PaymentRepository;
import com.trainbooking.it25100977.repository.TicketRepository;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Service class handling mock payment processing, e-ticket generation, QR encoding, and PDF generation.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** The checkout's payment, confirmation and ticket either commit together or roll back. */
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = PaymentDeclinedException.class)
    public Ticket payAndIssue(Long bookingId, PaymentRequest request) {
        processPayment(bookingId, request);
        return generateTicket(bookingId);
    }

    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final BookingRepository bookingRepository;
    private final com.trainbooking.it25102925.service.EmailService emailService;
    private final com.trainbooking.it25102327.repository.ScheduleRepository scheduleRepository;
    private final jakarta.persistence.EntityManager entityManager;

    /**
     * Processes simulated card payment for a pending booking.
     * Supports failure simulation if card number ends with 0000 or CVV is 000.
     *
     * @param bookingId booking ID
     * @param request payment card details
     * @return {@link Payment} entity
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = PaymentDeclinedException.class)
    public Payment processPayment(Long bookingId, PaymentRequest request) {
        log.info("Processing payment for booking ID: {}", bookingId);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + bookingId));
        // An ownership lookup may already have loaded this entity in the request's
        // persistence context. Refresh it after locking to avoid approving stale PENDING state.
        if(entityManager!=null && entityManager.contains(booking)) entityManager.refresh(booking,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (booking.getStatus() == Booking.BookingStatus.CONFIRMED) {
            // A repeated submit must not create another payment for an already-paid booking.
            return paymentRepository.findByBookingId(bookingId)
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                    .orElseThrow(() -> new IllegalStateException("This reservation is already confirmed. Please check My Bookings."));
        }
        if (booking.getStatus() != Booking.BookingStatus.PENDING) {
            throw new IllegalStateException("This reservation is cancelled or unavailable. Please make a new reservation.");
        }
        if (booking.getLockExpiresAt() == null || !booking.getLockExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Your seat hold has expired. Please choose your seats again.");
        }
        // A valid hold does not override a cancelled, inactive or departed service.
        if(booking.getSchedule()==null || booking.getSchedule().getId()==null) throw new IllegalStateException("This journey needs staff review.");
        // Serialize eligibility with coordinator edits/maintenance before approving a charge.
        var schedule=scheduleRepository.findByIdForUpdate(booking.getSchedule().getId())
            .orElseThrow(()->new IllegalStateException("This service is no longer available."));
        if(entityManager!=null && entityManager.contains(schedule)) entityManager.refresh(schedule,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(entityManager!=null && schedule.getTrain()!=null) entityManager.refresh(schedule.getTrain());
        if(entityManager!=null && schedule.getRoute()!=null) entityManager.refresh(schedule.getRoute());
        booking.setSchedule(schedule);
        if(!booking.getLockExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Your seat hold has expired. Please choose your seats again.");
        }
        var journey = com.trainbooking.it25103308.dto.CheckoutRecovery.from(booking, LocalDateTime.now());
        if (!journey.canSelectAgain()) {
            throw new IllegalStateException(journey.message());
        }
        // Decline simulation trigger (card ending in 0000 or CVV 000)
        String cleanCard = validatePaymentDetails(request);
        Payment payment = paymentRepository.findByBookingId(bookingId).orElseGet(() -> Payment.builder().booking(booking).build());
        if (payment.getStatus() == Payment.PaymentStatus.COMPLETED || payment.getStatus() == Payment.PaymentStatus.CANCELLED) {
            throw new IllegalStateException("The payment record for this reservation needs staff review. No new payment was taken.");
        }
        payment.setAmount(booking.getTotalAmount());
        payment.setAttemptedAt(LocalDateTime.now());
        if (cleanCard.endsWith("0000") || "000".equals(request.getCvv())) {
            log.warn("Payment declined for booking ID: {} - simulated decline criteria matched.", bookingId);
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setPaidAt(null);
            payment.setTransactionRef("FAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            paymentRepository.save(payment);
            throw new PaymentDeclinedException("Card transaction declined by payment gateway: Insufficient funds or invalid card credentials. Please retry with a valid card before your 10-minute seat lock expires.");
        }

        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        payment.setTransactionRef("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        Payment savedPayment = paymentRepository.save(payment);

        booking.setStatus(Booking.BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        log.info("Payment completed for booking ID: {}, txn ref: {}", bookingId, savedPayment.getTransactionRef());
        return savedPayment;
    }

    /** Validate the demo's supported card format before any payment or booking write. */
    private String validatePaymentDetails(PaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Please provide payment details.");
        }
        try { com.trainbooking.validation.ContactRules.name(request.getCardHolderName(), "Cardholder name"); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Enter a cardholder name of 1–100 characters using letters, spaces, initials, apostrophes or hyphens."); }
        String card = request.getCardNumber();
        // This simulation supports 16 digits, optionally grouped by single spaces.
        // Do not strip letters or truncate extra digits into an accepted card number.
        if (card == null || !card.matches("[0-9]{4}( ?[0-9]{4}){3}")) {
            throw new IllegalArgumentException("Enter a 16-digit card number, with optional spaces between groups of four.");
        }
        if (request.getCvv() == null || !request.getCvv().matches("[0-9]{3}")) {
            throw new IllegalArgumentException("CVV must contain exactly 3 digits.");
        }
        String month = request.getExpiryMonth();
        String year = request.getExpiryYear();
        if (month == null || !month.matches("0[1-9]|1[0-2]")
                || year == null || !year.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("Choose a valid expiry month and year.");
        }
        YearMonth expiry = YearMonth.of(Integer.parseInt(year), Integer.parseInt(month));
        if (expiry.isBefore(YearMonth.now())) {
            throw new IllegalArgumentException("The card expiry date is in the past. Choose a current or future expiry month.");
        }
        return card.replace(" ", "");
    }

    /**
     * Generates a confirmed electronic ticket with QR code data for a paid booking and emails the passenger.
     *
     * @param bookingId booking ID
     * @return generated {@link Ticket}
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Ticket generateTicket(Long bookingId) {
        log.info("Generating ticket for booking ID: {}", bookingId);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + bookingId));
        if(entityManager!=null) entityManager.refresh(booking,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (booking.getStatus() != Booking.BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Complete payment for an active reservation before requesting a ticket.");
        }
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .orElseThrow(() -> new IllegalStateException("No completed payment exists for this reservation."));
        return ticketRepository.findByBookingId(bookingId).orElseGet(() -> {
            String ticketNumber = "TKT-" + java.time.Year.now().getValue() + "-" + (10000 + bookingId);

            Ticket ticket = Ticket.builder()
                    .booking(booking)
                    .payment(payment)
                    .ticketNumber(ticketNumber)
                    .qrCodeData(ticketNumber)
                    .isBoarded(false)
                    .emailDeliveryStatus("PENDING")
                    .build();

            Ticket saved = ticketRepository.save(ticket);
            log.info("Issued ticket ID: {} with number: {}", saved.getId(), saved.getTicketNumber());

            scheduleTicketEmail(saved, booking.getPassenger() == null ? null : booking.getPassenger().getEmail());

            return saved;
        });
    }

    private void scheduleTicketEmail(Ticket ticket, String recipient) {
        Long ticketId = ticket.getId();
        String subject = "TrainBook demo ticket " + ticket.getTicketNumber();
        String body = "Your TrainBook reservation is confirmed. The demo ticket is attached. No real payment was collected.";
        Runnable delivery = () -> {
            String state = "FAILED";
            try {
                if (!emailService.isEnabled()) state = "DISABLED";
                else if (recipient != null && ticketId != null) {
                    var result = emailService.sendEmailWithAttachment(recipient, subject, body,
                            generateTicketPdf(ticketId), "ticket-" + ticketId + ".pdf");
                    state = result == null ? "FAILED" : result.name();
                }
            } catch (Exception failed) { log.warn("Ticket is retained; email failed for ticket #{}", ticketId); }
            try { if (ticketId != null) ticketRepository.updateDeliveryStatus(ticketId, state); }
            catch (Exception statusFailure) { log.warn("Ticket email status could not be recorded for #{}", ticketId); }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { delivery.run(); }
                });
        } else delivery.run();
    }

    /**
     * Generates QR code image bytes as PNG using ZXing.
     *
     * @param ticketId ticket ID
     * @return byte array containing the PNG image
     */
    /**
     * Generates QR code image bytes as PNG using ZXing.
     *
     * @param ticketId ticket ID
     * @return byte array containing the PNG image
     */
    @Transactional(readOnly = true)
    public byte[] generateQrCodeImage(Long ticketId) {
        Ticket ticket = getTicketById(ticketId);
        String qrContent = ticket.getTicketNumber();
        if (qrContent == null || qrContent.isBlank()) throw new TicketDocumentException("The ticket identifier is unavailable.", null);
        return createQrCodeBytes(qrContent);
    }

    /**
     * Encodes arbitrary string content into high-contrast QR code image PNG bytes.
     *
     * @param content text data to encode
     * @return PNG image bytes
     */
    private byte[] createQrCodeBytes(String content) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return pngOutputStream.toByteArray();
        } catch (Exception ex) {
            log.error("Failed to generate QR code bytes for content: {}", content, ex);
            throw new TicketDocumentException("The ticket QR image could not be generated.", ex);
        }
    }

    /**
     * Generates an authentic, publication-quality Sri Lanka Railways electronic ticket (boarding pass) PDF.
     * Includes embedded ZXing QR code, journey stations, departure/arrival times, seat allocations,
     * passenger verification data, and security hash.
     *
     * @param ticketId ticket ID
     * @return byte array containing the PDF document
     */
    @Transactional(readOnly = true)
    public byte[] generateTicketPdf(Long ticketId) {
        log.info("Generating professional high-definition PDF ticket for ID: {}", ticketId);
        Ticket ticket = getTicketById(ticketId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            WriterProperties writerProperties = new WriterProperties();
            writerProperties.setCompressionLevel(0);
            PdfWriter writer = new PdfWriter(baos, writerProperties);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(28f, 32f, 28f, 32f);

            PdfFont fontBold = TicketPdfFonts.create(ticket, StandardFonts.HELVETICA_BOLD);
            PdfFont fontRegular = TicketPdfFonts.create(ticket, StandardFonts.HELVETICA);
            PdfFont fontOblique = TicketPdfFonts.create(ticket, StandardFonts.HELVETICA_OBLIQUE);

            DeviceRgb navyColor = new DeviceRgb(26, 54, 93);      // #1A365D - Sri Lanka Railways Blue
            DeviceRgb goldColor = new DeviceRgb(197, 140, 31);    // #C58C1F - Royal Rail Gold
            DeviceRgb darkText = new DeviceRgb(45, 55, 72);       // #2D3748 - Dark Charcoal
            DeviceRgb mutedText = new DeviceRgb(113, 128, 150);   // #718096 - Muted gray
            DeviceRgb bgCard = new DeviceRgb(247, 250, 252);      // #F7FAFC - Soft background
            DeviceRgb borderLight = new DeviceRgb(226, 232, 240); // #E2E8F0 - Clean border
            DeviceRgb greenValid = new DeviceRgb(39, 148, 77);    // #27944D - Status Green

            String ticketNum = (ticket.getTicketNumber() != null && !ticket.getTicketNumber().isBlank())
                    ? ticket.getTicketNumber() : "TKT-2026-" + ticketId;
            String passengerName = (ticket.getPassengerName() != null && !ticket.getPassengerName().isBlank())
                    ? ticket.getPassengerName() : "Valued Passenger";
            String passengerNic = (ticket.getPassengerNic() != null && !ticket.getPassengerNic().isBlank())
                    ? ticket.getPassengerNic() : "N/A";
            String contactPhone = (ticket.getBooking() != null && ticket.getBooking().getContactPhone() != null && !ticket.getBooking().getContactPhone().isBlank())
                    ? ticket.getBooking().getContactPhone() : "N/A";
            String trainName = (ticket.getTrainName() != null && !ticket.getTrainName().isBlank())
                    ? ticket.getTrainName() : "Sri Lanka Railways Express";
            String origin = (ticket.getOrigin() != null && !ticket.getOrigin().isBlank())
                    ? ticket.getOrigin() : "Colombo Fort";
            String destination = (ticket.getDestination() != null && !ticket.getDestination().isBlank())
                    ? ticket.getDestination() : "Kandy";
            String depTime = (ticket.getDepartureTime() != null && !ticket.getDepartureTime().isBlank())
                    ? ticket.getDepartureTime() : "05:55 AM";
            String arrTime = (ticket.getArrivalTime() != null && !ticket.getArrivalTime().isBlank())
                    ? ticket.getArrivalTime() : "09:20 AM";
            String travelDate = (ticket.getTravelDate() != null)
                    ? ticket.getTravelDate().toString() : java.time.LocalDate.now().toString();
            String seatClass = (ticket.getSeatClass() != null && !ticket.getSeatClass().isBlank())
                    ? ticket.getSeatClass() : "FIRST";
            String seatNumbers = (ticket.getSeatNumbers() != null && !ticket.getSeatNumbers().isBlank())
                    ? ticket.getSeatNumbers() : "Car 01 / Seat 01";
            int seatCount = (ticket.getSeatCount() != null) ? ticket.getSeatCount() : 1;
            String farePaid = (ticket.getFarePaid() != null)
                    ? String.format(java.util.Locale.ROOT,"%,.2f", ticket.getFarePaid()) : "1,500.00";
            String txnRef = (ticket.getPayment() != null && ticket.getPayment().getTransactionRef() != null)
                    ? ticket.getPayment().getTransactionRef() : "TXN-" + ticketId;
            String status = (ticket.getStatus() != null) ? ticket.getStatus() : "CONFIRMED";

            // 1. TOP HEADER TABLE (Branding & Ticket No)
            Table headerTable = new Table(UnitValue.createPercentArray(new float[]{65, 35}));
            headerTable.setWidth(UnitValue.createPercentValue(100)).setFixedLayout();

            Cell titleCell = new Cell().setBorder(Border.NO_BORDER).setPadding(0);
            titleCell.add(new Paragraph("TRAINBOOK")
                    .setFont(fontBold).setFontSize(18f).setFontColor(navyColor).setMargin(0));
            titleCell.add(new Paragraph("Demo Electronic Passenger Ticket & Boarding Pass")
                    .setFont(fontRegular).setFontSize(9.5f).setFontColor(mutedText).setMarginTop(2f).setMarginBottom(0));
            titleCell.add(new Paragraph("SLIIT SE2030 - Sri Lanka train-booking demonstration")
                    .setFont(fontOblique).setFontSize(8f).setFontColor(goldColor).setMarginTop(1f).setMarginBottom(0));
            headerTable.addCell(titleCell);

            Cell badgeCell = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT).setPadding(0);
            badgeCell.add(new Paragraph("BOARDING PASS / E-TICKET")
                    .setFont(fontBold).setFontSize(8.5f).setFontColor(goldColor).setMargin(0));
            badgeCell.add(new Paragraph(ticketNum)
                    .setFont(fontBold).setFontSize(14f).setFontColor(navyColor).setMarginTop(2f).setMarginBottom(0));
            badgeCell.add(new Paragraph("Status: " + status)
                    .setFont(fontBold).setFontSize(9f).setFontColor(greenValid).setMarginTop(2f).setMarginBottom(0));
            headerTable.addCell(badgeCell);

            document.add(headerTable);

            // Accent dividing bar
            SolidLine solidGold = new SolidLine(2.5f);
            solidGold.setColor(goldColor);
            LineSeparator goldBar = new LineSeparator(solidGold);
            goldBar.setMarginTop(8f);
            goldBar.setMarginBottom(12f);
            document.add(goldBar);

            // 2. JOURNEY ROUTE HIGHLIGHT BOX
            Table routeCard = new Table(UnitValue.createPercentArray(new float[]{38, 24, 38}));
            routeCard.setWidth(UnitValue.createPercentValue(100)).setFixedLayout();
            routeCard.setBackgroundColor(bgCard);
            routeCard.setBorder(new SolidBorder(borderLight, 1f));

            // Origin
            Cell origCell = new Cell().setBorder(Border.NO_BORDER).setPadding(10f);
            origCell.add(new Paragraph("FROM (ORIGIN STATION)").setFont(fontBold).setFontSize(7.5f).setFontColor(mutedText).setMargin(0));
            origCell.add(new Paragraph(origin).setFont(fontBold).setFontSize(14f).setFontColor(navyColor).setMarginTop(2f).setMarginBottom(0));
            origCell.add(new Paragraph("Dep: " + depTime).setFont(fontBold).setFontSize(10f).setFontColor(darkText).setMarginTop(3f).setMarginBottom(0));
            routeCard.addCell(origCell);

            // Mid arrow & train
            Cell midCell = new Cell().setBorder(Border.NO_BORDER).setPadding(10f).setTextAlignment(TextAlignment.CENTER);
            midCell.add(new Paragraph("TRAIN SERVICE").setFont(fontBold).setFontSize(8f).setFontColor(goldColor).setMargin(0));
            midCell.add(new Paragraph("---------->").setFont(fontBold).setFontSize(11f).setFontColor(mutedText).setMarginTop(2f).setMarginBottom(0));
            midCell.add(new Paragraph(trainName).setFont(fontBold).setFontSize(9.5f).setFontColor(darkText).setMarginTop(2f).setMarginBottom(0));
            midCell.add(new Paragraph("Date: " + travelDate).setFont(fontRegular).setFontSize(8.5f).setFontColor(mutedText).setMarginTop(1f).setMarginBottom(0));
            routeCard.addCell(midCell);

            // Destination
            Cell destCell = new Cell().setBorder(Border.NO_BORDER).setPadding(10f).setTextAlignment(TextAlignment.RIGHT);
            destCell.add(new Paragraph("TO (DESTINATION STATION)").setFont(fontBold).setFontSize(7.5f).setFontColor(mutedText).setMargin(0));
            destCell.add(new Paragraph(destination).setFont(fontBold).setFontSize(14f).setFontColor(navyColor).setMarginTop(2f).setMarginBottom(0));
            destCell.add(new Paragraph("Arr: " + arrTime).setFont(fontBold).setFontSize(10f).setFontColor(darkText).setMarginTop(3f).setMarginBottom(0));
            routeCard.addCell(destCell);

            document.add(routeCard);

            // Spacing
            document.add(new Paragraph("").setMarginTop(10f).setMarginBottom(0));

            // 3. PASSENGER & RESERVATION DETAILS (4-column grid)
            Table detailsTable = new Table(UnitValue.createPercentArray(new float[]{28, 24, 24, 24}));
            detailsTable.setWidth(UnitValue.createPercentValue(100)).setFixedLayout();

            // Row 1
            addDetailCell(detailsTable, "PASSENGER NAME", passengerName, fontBold, fontRegular, navyColor, darkText, borderLight);
            addDetailCell(detailsTable, ticket.getDocumentLabel().toUpperCase(java.util.Locale.ROOT) + " NO.", passengerNic, fontBold, fontRegular, mutedText, darkText, borderLight);
            addDetailCell(detailsTable, "CONTACT PHONE", contactPhone, fontBold, fontRegular, mutedText, darkText, borderLight);
            addDetailCell(detailsTable, "BOOKING REF", txnRef, fontBold, fontRegular, mutedText, darkText, borderLight);

            // Row 2
            addDetailCell(detailsTable, "CLASS OF TRAVEL", seatClass + " CLASS", fontBold, fontRegular, mutedText, navyColor, borderLight);
            addDetailCell(detailsTable, "SEATS BOOKED", seatCount + (seatCount > 1 ? " Seats" : " Seat"), fontBold, fontRegular, mutedText, darkText, borderLight);
            addDetailCell(detailsTable, "ALLOCATED SEAT(S)", seatNumbers, fontBold, fontRegular, mutedText, navyColor, borderLight);
            addDetailCell(detailsTable, "TOTAL FARE PAID", "LKR " + farePaid, fontBold, fontRegular, mutedText, greenValid, borderLight);

            document.add(detailsTable);

            // 4. PERFORATION LINE
            document.add(new Paragraph("").setMarginTop(12f).setMarginBottom(0));
            DashedLine dashedMuted = new DashedLine(1.2f);
            dashedMuted.setColor(mutedText);
            LineSeparator perfLine = new LineSeparator(dashedMuted);
            document.add(perfLine);
            document.add(new Paragraph("ELECTRONIC VERIFICATION STUB & BOARDING PASS")
                    .setFont(fontBold).setFontSize(7.5f).setFontColor(mutedText)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(3f).setMarginBottom(10f));

            // 5. QR CODE & VERIFICATION SECTION (2-column layout)
            Table qrSection = new Table(UnitValue.createPercentArray(new float[]{32, 68}));
            qrSection.setWidth(UnitValue.createPercentValue(100)).setFixedLayout();
            qrSection.setBorder(new SolidBorder(borderLight, 1f));
            qrSection.setBackgroundColor(bgCard);

            // QR Cell
            Cell qrCell = new Cell().setBorder(Border.NO_BORDER).setPadding(12f).setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE);
            byte[] qrBytes = createQrCodeBytes(ticketNum);
            if (qrBytes != null && qrBytes.length > 0) {
                ImageData qrData = ImageDataFactory.create(qrBytes);
                Image qrImage = new Image(qrData);
                qrImage.setWidth(115f);
                qrImage.setHeight(115f);
                qrImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
                qrCell.add(qrImage);
            }
            qrCell.add(new Paragraph("SCAN AT STATION BARRIER").setFont(fontBold).setFontSize(7.5f).setFontColor(navyColor).setMarginTop(4f).setMarginBottom(0));
            qrCell.add(new Paragraph(ticketNum).setFont(fontBold).setFontSize(8f).setFontColor(darkText).setMarginTop(1f).setMarginBottom(0));
            qrSection.addCell(qrCell);

            // Instructions & Security Cell
            Cell infoCell = new Cell().setBorder(Border.NO_BORDER).setPadding(12f).setVerticalAlignment(VerticalAlignment.MIDDLE);
            infoCell.add(new Paragraph("DIGITAL BOARDING INSTRUCTIONS")
                    .setFont(fontBold).setFontSize(10.5f).setFontColor(navyColor).setMargin(0));

            Paragraph rules = new Paragraph()
                    .setFont(fontRegular).setFontSize(8.2f).setFontColor(darkText).setMultipliedLeading(1.35f).setMarginTop(6f).setMarginBottom(0);
            rules.add("• Present this QR code to the station gate turnstiles or conductor for instant barcode validation.\n");
            rules.add("• Passengers must carry a valid government-issued photo ID (NIC/Passport) matching the ticket name.\n");
            rules.add("• Please arrive at the platform at least 20 minutes before departure time.\n");
            rules.add("• Reservation is strictly valid only for the designated train service, travel date, and allocated seats.\n");
            rules.add("- Demonstration ticket; no real payment or railway entitlement.");
            infoCell.add(rules);

            // Security hash box
            String secHash = ticketNum;
            infoCell.add(new Paragraph("Ticket Reference: " + secHash)
                    .setFont(fontOblique).setFontSize(7.5f).setFontColor(mutedText).setMarginTop(8f).setMarginBottom(0));

            qrSection.addCell(infoCell);
            document.add(qrSection);

            // 6. FOOTER
            document.add(new Paragraph("TrainBook | SLIIT SE2030 demonstration | No real charge or railway travel entitlement")
                    .setFont(fontRegular).setFontSize(7.5f).setFontColor(mutedText)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(18f).setMarginBottom(0));

            document.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            log.error("Failed to generate styled PDF ticket for ticket ID: {}", ticketId, ex);
            throw new TicketDocumentException("The ticket PDF could not be generated.", ex);
        }
    }

    /**
     * Helper to render a structured detail cell within the passenger reservation grid.
     */
    private void addDetailCell(Table table, String label, String value, PdfFont boldFont, PdfFont regularFont,
                               DeviceRgb labelColor, DeviceRgb valueColor, DeviceRgb borderColor) {
        Cell cell = new Cell().setBorder(new SolidBorder(borderColor, 0.8f)).setPadding(6f);
        cell.add(new Paragraph(label).setFont(boldFont).setFontSize(7f).setFontColor(labelColor).setMargin(0));
        cell.add(new Paragraph(value).setFont(boldFont).setFontSize(9.5f).setFontColor(valueColor).setMarginTop(2f).setMarginBottom(0));
        table.addCell(cell);
    }

    /**
     * Generates a formal Tax Invoice and Receipt PDF document for a booking using iText 8.
     *
     * @param ticketId ticket ID
     * @return PDF byte array
     */
    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(Long ticketId) {
        log.info("Generating simulated payment receipt PDF for ticket ID: {}", ticketId);
        Ticket ticket = getTicketById(ticketId);
        String txnRef = (ticket.getPayment() != null && ticket.getPayment().getTransactionRef() != null)
                ? ticket.getPayment().getTransactionRef()
                : "TXN-" + ticket.getId();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            WriterProperties writerProperties = new WriterProperties();
            writerProperties.setCompressionLevel(0); // Leaves text streams uncompressed for verification
            PdfWriter writer = new PdfWriter(baos, writerProperties);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(32f, 36f, 32f, 36f);

            PdfFont fontBold = TicketPdfFonts.create(ticket, StandardFonts.HELVETICA_BOLD);
            PdfFont fontRegular = TicketPdfFonts.create(ticket, StandardFonts.HELVETICA);
            PdfFont fontOblique = TicketPdfFonts.create(ticket, StandardFonts.HELVETICA_OBLIQUE);

            DeviceRgb navyColor = new DeviceRgb(26, 54, 93);
            DeviceRgb darkText = new DeviceRgb(45, 55, 72);
            DeviceRgb mutedText = new DeviceRgb(113, 128, 150);
            DeviceRgb bgCard = new DeviceRgb(247, 250, 252);
            DeviceRgb borderLight = new DeviceRgb(226, 232, 240);
            DeviceRgb greenValid = new DeviceRgb(39, 148, 77);

            // Invoice Header
            Table header = new Table(UnitValue.createPercentArray(new float[]{60, 40}));
            header.setWidth(UnitValue.createPercentValue(100)).setFixedLayout();

            Cell titleCell = new Cell().setBorder(Border.NO_BORDER).setPadding(0);
            titleCell.add(new Paragraph("TrainBook - Demo Payment Receipt")
                    .setFont(fontBold).setFontSize(16f).setFontColor(navyColor).setMargin(0));
            titleCell.add(new Paragraph("SLIIT SE2030 demonstration - no real charge or tax invoice")
                    .setFont(fontRegular).setFontSize(8.5f).setFontColor(mutedText).setMarginTop(2f).setMarginBottom(0));
            header.addCell(titleCell);

            Cell invCell = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT).setPadding(0);
            invCell.add(new Paragraph("PAYMENT RECEIPT")
                    .setFont(fontBold).setFontSize(12f).setFontColor(navyColor).setMargin(0));
            invCell.add(new Paragraph("Receipt Ref: RCP-" + ticket.getTicketNumber())
                    .setFont(fontRegular).setFontSize(9f).setFontColor(darkText).setMarginTop(2f).setMarginBottom(0));
            invCell.add(new Paragraph("Paid at: " + (ticket.getPayment()!=null && ticket.getPayment().getPaidAt()!=null ? ticket.getPayment().getPaidAt().toString() : "Not recorded"))
                    .setFont(fontRegular).setFontSize(8.5f).setFontColor(mutedText).setMarginTop(1f).setMarginBottom(0));
            header.addCell(invCell);
            document.add(header);

            SolidLine solidNavy = new SolidLine(1.5f);
            solidNavy.setColor(navyColor);
            LineSeparator line = new LineSeparator(solidNavy);
            line.setMarginTop(8f);
            line.setMarginBottom(12f);
            document.add(line);

            // Customer & Service details
            Table custTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
            custTable.setWidth(UnitValue.createPercentValue(100)).setFixedLayout();

            Cell bCell = new Cell().setBorder(Border.NO_BORDER).setPadding(4f);
            bCell.add(new Paragraph("BILLED TO:").setFont(fontBold).setFontSize(8f).setFontColor(mutedText));
            bCell.add(new Paragraph(ticket.getPassengerName()==null ? "Not recorded" : ticket.getPassengerName()).setFont(fontBold).setFontSize(11f).setFontColor(darkText));
            bCell.add(new Paragraph(ticket.getDocumentLabel() + ": " + ticket.getPassengerNic()).setFont(fontRegular).setFontSize(8.5f).setFontColor(mutedText));
            custTable.addCell(bCell);

            Cell pCell = new Cell().setBorder(Border.NO_BORDER).setPadding(4f).setTextAlignment(TextAlignment.RIGHT);
            pCell.add(new Paragraph("PAYMENT DETAILS:").setFont(fontBold).setFontSize(8f).setFontColor(mutedText));
            pCell.add(new Paragraph("Status: PAID / COMPLETED").setFont(fontBold).setFontSize(9.5f).setFontColor(greenValid));
            pCell.add(new Paragraph("Transaction Ref: " + txnRef).setFont(fontRegular).setFontSize(8.5f).setFontColor(mutedText));
            custTable.addCell(pCell);
            document.add(custTable);

            document.add(new Paragraph("").setMarginTop(10f).setMarginBottom(0));

            // Line items table
            Table items = new Table(UnitValue.createPercentArray(new float[]{45, 20, 15, 20}));
            items.setWidth(UnitValue.createPercentValue(100)).setFixedLayout();

            // Header
            items.addHeaderCell(new Cell().setBackgroundColor(bgCard).setBorder(new SolidBorder(borderLight, 1f)).setPadding(6f)
                    .add(new Paragraph("DESCRIPTION / SERVICE").setFont(fontBold).setFontSize(8.5f).setFontColor(navyColor)));
            items.addHeaderCell(new Cell().setBackgroundColor(bgCard).setBorder(new SolidBorder(borderLight, 1f)).setPadding(6f)
                    .add(new Paragraph("SEATS / CLASS").setFont(fontBold).setFontSize(8.5f).setFontColor(navyColor)));
            items.addHeaderCell(new Cell().setBackgroundColor(bgCard).setBorder(new SolidBorder(borderLight, 1f)).setPadding(6f).setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph("QTY").setFont(fontBold).setFontSize(8.5f).setFontColor(navyColor)));
            items.addHeaderCell(new Cell().setBackgroundColor(bgCard).setBorder(new SolidBorder(borderLight, 1f)).setPadding(6f).setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph("AMOUNT (LKR)").setFont(fontBold).setFontSize(8.5f).setFontColor(navyColor)));

            // Row
            String desc = "Train " + ticket.getTrainName() + " (" + ticket.getOrigin() + " to " + ticket.getDestination() + ") on " + ticket.getTravelDate();
            items.addCell(new Cell().setBorder(new SolidBorder(borderLight, 1f)).setPadding(6f)
                    .add(new Paragraph(desc).setFont(fontRegular).setFontSize(8.5f)));
            items.addCell(new Cell().setBorder(new SolidBorder(borderLight, 1f)).setPadding(6f)
                    .add(new Paragraph(ticket.getSeatNumbers() + " (" + ticket.getSeatClass() + ")").setFont(fontRegular).setFontSize(8.5f)));
            items.addCell(new Cell().setBorder(new SolidBorder(borderLight, 1f)).setPadding(6f).setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph(String.valueOf(ticket.getSeatCount())).setFont(fontRegular).setFontSize(8.5f)));
            items.addCell(new Cell().setBorder(new SolidBorder(borderLight, 1f)).setPadding(6f).setTextAlignment(TextAlignment.RIGHT)
                    .add(new Paragraph(String.format(java.util.Locale.ROOT,"%,.2f", ticket.getFarePaid())).setFont(fontBold).setFontSize(8.5f)));

            document.add(items);

            // Total Block
            Table totalTable = new Table(UnitValue.createPercentArray(new float[]{65, 35}));
            totalTable.setWidth(UnitValue.createPercentValue(100)).setFixedLayout();
            totalTable.setMarginTop(8f);

            Cell noteCell = new Cell().setBorder(Border.NO_BORDER).setPadding(4f);
            noteCell.add(new Paragraph("TrainBook demonstration receipt. No real charge was made.")
                    .setFont(fontOblique).setFontSize(8.5f).setFontColor(mutedText));
            totalTable.addCell(noteCell);

            Cell totCell = new Cell().setBorder(Border.NO_BORDER).setPadding(4f).setTextAlignment(TextAlignment.RIGHT);
            totCell.add(new Paragraph("Total Paid: LKR " + String.format(java.util.Locale.ROOT,"%,.2f", ticket.getFarePaid()) + " (simulated)")
                    .setFont(fontBold).setFontSize(11f).setFontColor(navyColor));
            totalTable.addCell(totCell);
            document.add(totalTable);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate receipt PDF for ID: {}", ticketId, e);
            throw new TicketDocumentException("The receipt PDF could not be generated.", e);
        }
    }

    /**
     * Retrieves ticket details by ID.
     *
     * @param id ticket ID
     * @return {@link Ticket} entity
     */
    @Transactional(readOnly = true)
    public java.util.Optional<Ticket> findTicketForBooking(Long bookingId) {
        return ticketRepository.findByBookingId(bookingId);
    }

    public Ticket getTicketById(Long id) {
        log.info("Fetching ticket by ID: {}", id);
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with ID: " + id));
    }
}
