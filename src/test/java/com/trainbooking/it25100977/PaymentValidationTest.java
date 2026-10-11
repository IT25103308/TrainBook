package com.trainbooking.it25100977;

import com.trainbooking.it25100977.controller.PaymentController;
import com.trainbooking.it25100977.dto.PaymentRequest;
import com.trainbooking.it25100977.model.Payment;
import com.trainbooking.it25100977.repository.PaymentRepository;
import com.trainbooking.it25100977.repository.TicketRepository;
import com.trainbooking.it25100977.service.PaymentService;
import com.trainbooking.it25102925.service.EmailService;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regression checks for malformed payment details; no application or live database is started. */
class PaymentValidationTest {
    private BookingRepository bookings;
    private PaymentRepository payments;
    private TicketRepository tickets;
    private PaymentService service;
    private Booking booking;
    private LocalDateTime originalExpiry;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingRepository.class);
        payments = mock(PaymentRepository.class);
        tickets = mock(TicketRepository.class);
        var schedules=mock(com.trainbooking.it25102327.repository.ScheduleRepository.class);
        service = new PaymentService(payments, tickets, bookings, mock(EmailService.class),schedules, null);
        originalExpiry = LocalDateTime.now().plusMinutes(5);
        booking = Booking.builder().id(17L).status(Booking.BookingStatus.PENDING)
                .totalAmount(new BigDecimal("3000.00")).lockExpiresAt(originalExpiry).build();
        booking.setTravelDate(java.time.LocalDate.now().plusDays(1));
        booking.setSchedule(com.trainbooking.it25102327.model.Schedule.builder().id(1L).isActive(true)
                .dayOfWeek(booking.getTravelDate().getDayOfWeek()).departureTime(java.time.LocalTime.of(8, 30))
                .train(com.trainbooking.it25102327.model.Train.builder().id(1L)
                        .status(com.trainbooking.it25102327.model.Train.TrainStatus.ON_TIME).build()).build());
        when(schedules.findByIdForUpdate(1L)).thenAnswer(call->Optional.of(booking.getSchedule()));
        when(bookings.findByIdForUpdate(17L)).thenReturn(Optional.of(booking));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "00", "0000", "1234", "12345", "12a", "+12", "12 ", " 123", "１２３"})
    void malformedCvvCannotCreatePaymentOrConfirmBooking(String cvv) {
        PaymentRequest request = validRequest();
        request.setCvv(cvv);
        assertRejectedWithoutWrites(request, "exactly 3 digits");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0000", "411122223333444", "41112222333344444", "4111222233334444444",
            "41112222333344444444", "4111 2222 3333 44444", "4111-2222-3333-4444", "4111  2222 3333 4444",
            "4111\t2222 3333 4444", "411122223333444a", " 4111222233334444", "４１１１２２２２３３３３４４４４"})
    void malformedCardNumberCannotCreatePaymentOrConfirmBooking(String card) {
        PaymentRequest request = validRequest();
        request.setCardNumber(card);
        assertRejectedWithoutWrites(request, "16-digit");
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111222233334444", "4111 2222 3333 4444"})
    void validDemoFormatCanConfirmBookingWithoutExtendingHold(String card) {
        PaymentRequest request = validRequest();
        request.setCardNumber(card);
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Payment payment = service.processPayment(17L, request);
        assertEquals(Payment.PaymentStatus.COMPLETED, payment.getStatus());
        assertEquals(Booking.BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(originalExpiry, booking.getLockExpiresAt());
        assertEquals(new BigDecimal("3000.00"), payment.getAmount());
        verify(payments).save(any());
        verify(bookings).save(booking);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111222233330000", "4111 2222 3333 0000"})
    void validLengthCardEndingInFourZerosStillDeclines(String card) {
        PaymentRequest request = validRequest();
        request.setCardNumber(card);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.processPayment(17L, request));
        assertTrue(error.getMessage().contains("declined"));
        assertPendingWithOriginalExpiry();
        verify(payments).save(argThat(payment -> payment.getStatus() == Payment.PaymentStatus.FAILED));
        verify(bookings, never()).save(any());
        verifyNoInteractions(tickets);
    }

    @Test
    void threeZeroCvvStillDeclinesWhileFourZerosIsAnInputError() {
        PaymentRequest request = validRequest();
        request.setCvv("000");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.processPayment(17L, request));
        assertTrue(error.getMessage().contains("declined"));
        assertPendingWithOriginalExpiry();
        verify(payments).save(argThat(payment -> payment.getStatus() == Payment.PaymentStatus.FAILED));
        verify(bookings, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "1234", "<script>", "---"})
    void missingCardholderNameIsRejected(String name) {
        PaymentRequest request = validRequest();
        request.setCardHolderName(name);
        assertRejectedWithoutWrites(request, "cardholder name");
    }

    @Test
    void excessiveCardholderNameIsRejected() {
        PaymentRequest request = validRequest();
        request.setCardHolderName("A".repeat(101));
        assertRejectedWithoutWrites(request, "cardholder name");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"00", "13", "1", "abc", " 12"})
    void invalidExpiryMonthIsRejected(String month) {
        PaymentRequest request = validRequest();
        request.setExpiryMonth(month);
        assertRejectedWithoutWrites(request, "expiry month and year");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"26", "20267", "abcd", " 2027"})
    void invalidExpiryYearIsRejected(String year) {
        PaymentRequest request = validRequest();
        request.setExpiryYear(year);
        assertRejectedWithoutWrites(request, "expiry month and year");
    }

    @Test
    void expiredCardIsRejectedButCurrentExpiryMonthIsAllowed() {
        PaymentRequest request = validRequest();
        setExpiry(request, YearMonth.now().minusMonths(1));
        assertRejectedWithoutWrites(request, "in the past");
        setExpiry(request, YearMonth.now());
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals(Payment.PaymentStatus.COMPLETED, service.processPayment(17L, request).getStatus());
    }

    @Test
    void missingRequestIsRejected() {
        assertRejectedWithoutWrites(null, "provide payment details");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cvv", "cardNumber"})
    void directFormSubmissionCannotBypassBrowserValidation(String field) throws Exception {
        var ownerBookings = mock(com.trainbooking.it25103308.service.BookingService.class);
        when(ownerBookings.getBookingById(17L)).thenReturn(com.trainbooking.it25103308.model.Booking.builder().passenger(com.trainbooking.it25101520.model.User.builder().email("own@test.invalid").build()).build());
        var mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service, ownerBookings)).build();
        var request = validRequest();
        mvc.perform(post("/payment/process").principal(() -> "own@test.invalid").param("bookingId", "17")
                        .param("cardHolderName", request.getCardHolderName())
                        .param("cardNumber", "cardNumber".equals(field) ? "41112222333344444" : request.getCardNumber())
                        .param("cvv", "cvv".equals(field) ? "0000" : request.getCvv())
                        .param("expiryMonth", request.getExpiryMonth()).param("expiryYear", request.getExpiryYear()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/booking/17/payment?declined=true"))
                .andExpect(flash().attribute("errorMessage", "cvv".equals(field)
                        ? "CVV must contain exactly 3 digits."
                        : "Enter a 16-digit card number, with optional spaces between groups of four."));
        verifyNoInteractions(payments, tickets);
        verify(bookings, never()).save(any());
        assertPendingWithOriginalExpiry();
    }

    private void assertRejectedWithoutWrites(PaymentRequest request, String message) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.processPayment(17L, request));
        assertTrue(error.getMessage().contains(message), error.getMessage());
        assertPendingWithOriginalExpiry();
        verifyNoInteractions(payments, tickets);
        verify(bookings, never()).save(any());
    }

    private void assertPendingWithOriginalExpiry() {
        assertEquals(Booking.BookingStatus.PENDING, booking.getStatus());
        assertEquals(originalExpiry, booking.getLockExpiresAt());
    }

    private PaymentRequest validRequest() {
        PaymentRequest request = PaymentRequest.builder().bookingId(17L).cardHolderName("Test Passenger")
                .cardNumber("4111222233334444").cvv("123").build();
        setExpiry(request, YearMonth.now().plusYears(1));
        return request;
    }

    private void setExpiry(PaymentRequest request, YearMonth expiry) {
        request.setExpiryMonth(String.format("%02d", expiry.getMonthValue()));
        request.setExpiryYear(String.valueOf(expiry.getYear()));
    }
}
