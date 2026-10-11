package com.trainbooking.it25100977;

import com.trainbooking.it25100977.model.*;
import com.trainbooking.it25100977.repository.*;
import com.trainbooking.it25100977.service.*;
import com.trainbooking.it25102925.service.EmailService;
import com.trainbooking.it25102327.model.*;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import java.io.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketDocumentsTest {
    TicketRepository repository;PaymentService service;Ticket ticket;Booking booking;
    @BeforeEach void setup() {
        repository=mock(TicketRepository.class);
        service=new PaymentService(mock(PaymentRepository.class),repository,mock(BookingRepository.class),mock(EmailService.class),mock(ScheduleRepository.class),null);
        booking=Booking.builder().id(20L).schedule(Schedule.builder().train(Train.builder().trainName("Demo Express").build())
            .route(Route.builder().origin("Demo Origin").destination("Demo Destination").build()).departureTime(LocalTime.of(23,0)).arrivalTime(LocalTime.of(2,0)).arrivalDayOffset(1).build())
            .travelDate(LocalDate.now().plusDays(1)).passengerName("Demo Passenger").documentType(Booking.DocumentType.FOREIGN_PASSPORT).passengerNic("AB1234567")
            .contactPhone("0771234567").seatClass(Booking.SeatClass.FIRST).numberOfSeats(2).seatNumbers("Car 01 / S03, Car 01 / S07").status(Booking.BookingStatus.CONFIRMED).build();
        ticket=Ticket.builder().id(10L).booking(booking).ticketNumber("TKT-DEMO-20").qrCodeData("TKT-DEMO-20").emailDeliveryStatus("DISABLED")
            .payment(Payment.builder().status(Payment.PaymentStatus.COMPLETED).amount(new BigDecimal("3000.00")).paidAt(LocalDateTime.of(2026,10,5,10,15)).transactionRef("TXN-DEMO-20").build()).build();
        when(repository.findById(10L)).thenReturn(Optional.of(ticket));
    }
    private String text(byte[] bytes) throws Exception {
        assertEquals("%PDF",new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII));
        try(var pdf=new PdfDocument(new PdfReader(new ByteArrayInputStream(bytes)))) {
            var text=new StringBuilder();for(int n=1;n<=pdf.getNumberOfPages();n++)text.append(PdfTextExtractor.getTextFromPage(pdf.getPage(n)));return text.toString();
        }
    }
    @Test void genuinePdfAndReceiptUseStoredJourneyDocumentAmountAndPaidTimeWithoutFalseTaxClaims() throws Exception {
        byte[] pdf=service.generateTicketPdf(10L),receipt=service.generateInvoicePdf(10L);String pdfText=text(pdf),receiptText=text(receipt);
        for(String expected:new String[]{"TKT-DEMO-20","Demo Passenger","PASSPORT NO.","AB1234567","Car 01 / S03","3,000.00","(+1 day)"})assertTrue(pdfText.contains(expected),expected);
        assertTrue(receiptText.contains("2026-10-05T10:15"));assertTrue(receiptText.contains("3,000.00"));
        assertFalse(receiptText.contains("VAT Reg"));assertFalse(receiptText.contains("VAT Included"));assertFalse(pdfText.contains("SHA256"));
        Files.createDirectories(Path.of("target/pdf-previews"));Files.write(Path.of("target/pdf-previews/demo-ticket.pdf"),pdf);Files.write(Path.of("target/pdf-previews/demo-receipt.pdf"),receipt);
    }
    @Test void generatedQrDecodesToTheActualIssuedTicketNumber() throws Exception {
        var png=ImageIO.read(new ByteArrayInputStream(service.generateQrCodeImage(10L)));
        var qr=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(png))));
        assertEquals(ticket.getTicketNumber(),qr.getText());
        Files.createDirectories(Path.of("target/pdf-previews"));Files.write(Path.of("target/pdf-previews/ticket-qr.png"),service.generateQrCodeImage(10L));
    }
    @Test void documentOrQrFailureCannotReturnPlainTextOrAnEmptyImageAsSuccess() {
        booking.setPassengerName("\uFFFF");
        assertThrows(TicketDocumentException.class,()->service.generateTicketPdf(10L));
        assertThrows(TicketDocumentException.class,()->service.generateInvoicePdf(10L));
        ticket.setTicketNumber("X".repeat(100000));assertThrows(TicketDocumentException.class,()->service.generateQrCodeImage(10L));
    }
    @Test void unicodePassengerNamesRemainPresentInThePdfText() throws Exception {
        booking.setPassengerName("අනුර පෙරේරා");byte[] pdf=service.generateTicketPdf(10L);assertTrue(text(pdf).contains("අනුර පෙරේරා"));
        Files.createDirectories(Path.of("target/pdf-previews"));Files.write(Path.of("target/pdf-previews/unicode-ticket.pdf"),pdf);
        booking.setPassengerName("அன்ஃபாஸ்");byte[] receipt=service.generateInvoicePdf(10L);assertTrue(text(receipt).contains("அன்ஃபாஸ்"));Files.write(Path.of("target/pdf-previews/unicode-receipt.pdf"),receipt);
    }
    @Test void maximumSupportedDetailsAndSixSeatsStayInsideReadablePdfPages() throws Exception {
        booking.setPassengerName("A".repeat(100));booking.setPassengerNic("AB123456789012345678");
        booking.getSchedule().getTrain().setTrainName("D".repeat(100));booking.getSchedule().getRoute().setOrigin("O".repeat(100));booking.getSchedule().getRoute().setDestination("S".repeat(100));
        booking.setNumberOfSeats(6);booking.setSeatNumbers("Car 01 / S01, Car 01 / S02, Car 01 / S03, Car 01 / S04, Car 01 / S05, Car 01 / S06");
        byte[] pdf=service.generateTicketPdf(10L);String content=text(pdf);assertTrue(content.contains("S06"));assertTrue(content.contains("AB123456789012345678"));
        try(var document=new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            assertTrue(document.getNumberOfPages()<=2);
            for(int page=1;page<=document.getNumberOfPages();page++) {
                float width=document.getPage(page).getPageSize().getWidth(),height=document.getPage(page).getPageSize().getHeight();
                new com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor(new com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener() {
                    public java.util.Set<com.itextpdf.kernel.pdf.canvas.parser.EventType> getSupportedEvents() {return java.util.Set.of(com.itextpdf.kernel.pdf.canvas.parser.EventType.RENDER_TEXT);}
                    public void eventOccurred(com.itextpdf.kernel.pdf.canvas.parser.data.IEventData data,com.itextpdf.kernel.pdf.canvas.parser.EventType type) {
                        var line=((com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo)data).getBaseline();
                        for(var point:java.util.List.of(line.getStartPoint(),line.getEndPoint())) {
                            assertTrue(point.get(0)>=0 && point.get(0)<=width,"PDF text crosses a horizontal page boundary");
                            assertTrue(point.get(1)>=0 && point.get(1)<=height,"PDF text crosses a vertical page boundary");
                        }
                    }
                }).processPageContent(document.getPage(page));
            }
        }
        Files.createDirectories(Path.of("target/pdf-previews"));Files.write(Path.of("target/pdf-previews/long-details-ticket.pdf"),pdf);
    }
}
