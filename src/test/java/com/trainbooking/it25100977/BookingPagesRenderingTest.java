package com.trainbooking.it25100977;

import com.trainbooking.it25101520.dto.BookingHistoryItem;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25100977.model.Payment;
import com.trainbooking.it25100977.model.Ticket;
import com.trainbooking.it25100977.repository.PaymentRepository;
import com.trainbooking.it25100977.repository.TicketRepository;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.it25100977.service.PaymentService;
import com.trainbooking.it25102925.service.EmailService;
import com.trainbooking.it25102327.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.*;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingPagesRenderingTest {
    private String render(String template, Map<String,Object> model) {
        var resolver=new ClassLoaderTemplateResolver(); resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8");
        var engine=new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var servlet=new MockServletContext(); var request=new MockHttpServletRequest(servlet); request.setRequestURI("/passenger/bookings");
        var exchange=JakartaServletWebApplication.buildApplication(servlet).buildExchange(request,new MockHttpServletResponse());
        return engine.process(template,new WebContext(exchange,Locale.ENGLISH,model));
    }
    private BookingHistoryItem row(long id,String status,Long ticketId) {
        return new BookingHistoryItem(id,LocalDateTime.of(2026,10,5,5,0),"Test train","Colombo Fort","Kandy",LocalDate.of(2026,10,6),LocalTime.of(8,30),
            "Car 01 / S03",1,"FIRST",new BigDecimal("1500.00"),status,"Test status",ticketId,ticketId==null?null:"TKT-ISSUED-20");
    }
    @Test void historyRendersActionsForEachStateAndCorrectPdfTicketId() throws Exception {
        var rows=List.of(row(24,"PENDING",null),row(23,"EXPIRED",null),row(22,"CANCELLED",null),row(20,"CONFIRMED",10L));
        String html=render("it25101520/booking-history",Map.of("bookings",rows,"selectedFilter","ALL"));
        assertTrue(html.contains("href=\"/booking/24/payment\"")); assertTrue(html.contains("Continue payment"));
        assertTrue(html.contains("href=\"/payment/ticket/10/download\"")); assertFalse(html.contains("/payment/ticket/20/download"));
        assertEquals(1,html.split("/download",-1).length-1); assertTrue(html.contains("Awaiting payment")); assertTrue(html.contains("Expired"));
        assertTrue(html.contains("Car 01 / S03")); assertTrue(html.contains("06 Oct 2026")); assertFalse(html.contains("2026-08-16"));
        assertTrue(html.contains("aria-current=\"page\"")); Files.writeString(Path.of("target/booking-history-preview.html"),html);
    }
    @Test void emptyFilteredHistoryOffersAUsefulWayBack() {
        String html=render("it25101520/booking-history",Map.of("bookings",List.of(),"selectedFilter","PENDING"));
        assertTrue(html.contains("No bookings in this category")); assertTrue(html.contains("View all bookings")); assertTrue(html.contains("Find trains"));
    }
    @ParameterizedTest
    @CsvSource({"NIC,200300901022,NIC", "SRI_LANKAN_PASSPORT,N1234567,Passport", "FOREIGN_PASSPORT,012345678,Passport"})
    void actualTicketPageAndBothPdfDocumentsUseTheDocumentChoice(String type,String number,String label) throws Exception {
        var schedule=Schedule.builder().train(Train.builder().trainName("Test train").build()).route(Route.builder().origin("Colombo Fort").destination("Kandy").build())
            .departureTime(LocalTime.of(8,30)).arrivalTime(LocalTime.of(11,5)).build();
        var booking=Booking.builder().id(20L).schedule(schedule).passengerName("Test Passenger").documentType(Booking.DocumentType.valueOf(type)).passengerNic(number)
            .travelDate(LocalDate.of(2026,10,6)).numberOfSeats(1).seatClass(Booking.SeatClass.FIRST).seatNumbers("Car 01 / S03").contactPhone("0771234567").status(Booking.BookingStatus.CONFIRMED).build();
        var payment=Payment.builder().amount(new BigDecimal("1500.00")).status(Payment.PaymentStatus.COMPLETED).transactionRef("TXN-TEST").build();
        var ticket=Ticket.builder().id(10L).booking(booking).payment(payment).ticketNumber("TKT-TEST").qrCodeData("TKT-TEST").emailDeliveryStatus("DISABLED").build();
        String html=render("it25100977/eticket",Map.of("ticket",ticket));
        assertTrue(html.contains(label+": "+number)); if(!"NIC".equals(label)) assertFalse(html.contains("NIC: "+number));
        assertFalse(html.contains("api.qrserver.com"));assertFalse(html.contains("Security Hash:"));assertTrue(html.contains("@media print"));
        assertTrue(html.contains("Email delivery is disabled for this local demo"));assertTrue(html.contains("Payment Receipt"));
        var tickets=mock(TicketRepository.class); when(tickets.findById(10L)).thenReturn(Optional.of(ticket));
        var service=new PaymentService(mock(PaymentRepository.class),tickets,mock(BookingRepository.class),mock(EmailService.class),mock(com.trainbooking.it25102327.repository.ScheduleRepository.class), null);
        String pdfText=text(service.generateTicketPdf(10L)); String receiptText=text(service.generateInvoicePdf(10L));
        Files.write(Path.of("target/eticket-qr-preview.png"),service.generateQrCodeImage(10L));
        assertTrue(pdfText.contains(label.toUpperCase()+" NO.")); assertTrue(pdfText.contains(number));
        assertTrue(receiptText.contains(label+": "+number));
        Files.writeString(Path.of("target/eticket-"+type+"-preview.html"),html);
    }
    private String text(byte[] bytes) throws Exception {
        assertTrue(new String(bytes,0,Math.min(4,bytes.length)).startsWith("%PDF"),"A text fallback must not pass as a PDF");
        try(var pdf=new PdfDocument(new PdfReader(new ByteArrayInputStream(bytes)))) {
            StringBuilder text=new StringBuilder(); for(int page=1;page<=pdf.getNumberOfPages();page++) text.append(PdfTextExtractor.getTextFromPage(pdf.getPage(page))); return text.toString();
        }
    }
}
