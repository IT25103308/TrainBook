package com.trainbooking.it25103308;

import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.service.PassengerService;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25103308.controller.BookingController;
import com.trainbooking.it25103308.dto.BookingRequest;
import com.trainbooking.it25103308.dto.CheckoutHold;
import com.trainbooking.it25103308.dto.CheckoutRecovery;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises actual form binding and redirects without a Spring application or database. */
class BookingControllerTest {
    @Test void signingInContinuesTheSameJourneyWithoutCreatingAHold() throws Exception {
        var date=LocalDate.now().plusDays(1);
        mvc.perform(get("/booking/sign-in").principal(()->"anfas@example.test").param("scheduleId","10").param("date",date.toString()).param("seatClass","SECOND"))
            .andExpect(redirectedUrl("/booking/seats?scheduleId=10&date="+date+"&seatClass=SECOND"));
        verifyNoInteractions(service,passengers);
    }
    private BookingService service;
    private PassengerService passengers;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(BookingService.class);
        passengers = mock(PassengerService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BookingController(
                service, mock(ScheduleRepository.class), passengers))
                .setControllerAdvice(new com.trainbooking.exception.GlobalExceptionHandler()).build();
    }

    @Test
    void submittedSeatLabelsAndQuantityReachServiceUnchanged() throws Exception {
        when(passengers.getUserByEmail("anfas@example.test")).thenReturn(User.builder().id(7L).build());
        when(service.createBooking(any(), eq(7L))).thenReturn(Booking.builder().id(42L).build());
        String chosen = "Car 02 / S03, Car 02 / S17";
        mvc.perform(post("/booking").principal(() -> "anfas@example.test")
                        .param("scheduleId", "10").param("travelDate", LocalDate.now().plusDays(1).toString())
                        .param("seatClass", "SECOND").param("seatCount", "2").param("seatNumbers", chosen))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/booking/42/payment"));
        ArgumentCaptor<BookingRequest> request = ArgumentCaptor.forClass(BookingRequest.class);
        verify(service).createBooking(request.capture(), eq(7L));
        assertEquals(chosen, request.getValue().getSeatNumbers());
        assertEquals(2, request.getValue().getNumberOfSeats());
        assertEquals("SECOND", request.getValue().getSeatClass());
    }

    @Test
    void conflictReturnsToRefreshedSeatMapWithMessageAndPassengerDetails() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        when(passengers.getUserByEmail("anfas@example.test")).thenReturn(User.builder().id(7L).build());
        when(service.createBooking(any(), eq(7L))).thenThrow(new IllegalStateException("Selected seat is already held."));
        var result = mvc.perform(post("/booking").principal(() -> "anfas@example.test")
                        .param("scheduleId", "10").param("travelDate", date.toString())
                        .param("seatClass", "FIRST").param("seatCount", "1")
                        .param("seatNumbers", "Car 01 / S03").param("passengerName", "Anfas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/booking/seats?scheduleId=10&date=" + date))
                .andExpect(flash().attribute("bookingError", "Selected seat is already held.")).andReturn();
        BookingRequest retained = (BookingRequest) result.getFlashMap().get("bookingRequest");
        assertEquals("Anfas", retained.getPassengerName());
        assertNull(retained.getSeatNumbers());
    }

    @Test
    void anonymousSubmissionCannotCreateBookingForFallbackUser() throws Exception {
        mvc.perform(post("/booking").param("scheduleId", "10"))
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(service, passengers);
    }

    @Test
    void malformedQuantityDoesNotReachService() throws Exception {
        mvc.perform(post("/booking").principal(() -> "anfas@example.test")
                        .param("scheduleId", "10").param("travelDate", LocalDate.now().plusDays(1).toString())
                        .param("seatCount", "not-a-number"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("bookingError"));
        verifyNoInteractions(service, passengers);
    }

    @Test
    void checkoutUsesSavedRemainingTimeInsteadOfRestartingTenMinutes() throws Exception {
        Booking booking = Booking.builder().passenger(User.builder().email("anfas@example.test").build()).id(11L).status(Booking.BookingStatus.PENDING)
                .lockExpiresAt(LocalDateTime.now().plusMinutes(3)).build();
        booking.setTravelDate(LocalDate.now().plusDays(1));
        booking.setSchedule(com.trainbooking.it25102327.model.Schedule.builder().id(10L).isActive(true)
                .dayOfWeek(booking.getTravelDate().getDayOfWeek()).departureTime(java.time.LocalTime.of(8, 30))
                .train(com.trainbooking.it25102327.model.Train.builder().status(com.trainbooking.it25102327.model.Train.TrainStatus.ON_TIME).build()).build());
        when(service.getBookingById(11L)).thenReturn(booking);
        var result = mvc.perform(get("/booking/11/payment").principal(() -> "anfas@example.test"))
                .andExpect(view().name("it25100977/payment")).andExpect(model().attribute("booking", booking))
                .andReturn();
        CheckoutHold hold = (CheckoutHold) result.getModelAndView().getModel().get("checkoutHold");
        assertTrue(hold.isPayable());
        assertTrue(hold.remainingMillis() > 0 && hold.remainingMillis() <= 180000);
    }

    @Test
    void expiredCheckoutRemainsUnavailableWhenRefreshed() throws Exception {
        when(service.getBookingById(11L)).thenReturn(Booking.builder().passenger(User.builder().email("anfas@example.test").build()).id(11L).status(Booking.BookingStatus.PENDING)
                .lockExpiresAt(LocalDateTime.now().minusSeconds(1)).build());
        var result = mvc.perform(get("/booking/11/payment").principal(() -> "anfas@example.test")).andExpect(view().name("it25100977/payment")).andReturn();
        CheckoutHold hold = (CheckoutHold) result.getModelAndView().getModel().get("checkoutHold");
        assertEquals("EXPIRED", hold.state());
        assertFalse(hold.isPayable());
    }

    @Test
    void checkoutIncludesFreshRecoveryEligibility() throws Exception {
        Booking booking = Booking.builder().passenger(User.builder().email("anfas@example.test").build()).id(11L).status(Booking.BookingStatus.CANCELLED).build();
        CheckoutRecovery recovery = new CheckoutRecovery(true, 60000,
                "/booking/seats?scheduleId=2&date=2026-10-06", "Check available seats.");
        when(service.getBookingById(11L)).thenReturn(booking);
        when(service.getCheckoutRecovery(11L)).thenReturn(recovery);
        mvc.perform(get("/booking/11/payment").principal(() -> "anfas@example.test")).andExpect(model().attribute("checkoutRecovery", recovery));
    }

    @Test
    void recoveryLinkRechecksJourneyBeforeOpeningFreshMap() throws Exception {
        when(service.getBookingById(11L)).thenReturn(Booking.builder().passenger(User.builder().email("anfas@example.test").build()).build());
        when(service.getCheckoutRecovery(11L)).thenReturn(new CheckoutRecovery(true, 60000,
                "/booking/seats?scheduleId=2&date=2026-10-06", "Check available seats."));
        mvc.perform(get("/booking/11/seats-again").principal(() -> "anfas@example.test"))
                .andExpect(redirectedUrl("/booking/seats?scheduleId=2&date=2026-10-06"));
        verify(service).getCheckoutRecovery(11L);
        verify(service, never()).createBooking(any(), any());
        verify(service, never()).releaseLock(any());
    }

    @Test
    void staleRecoveryLinkForUnavailableJourneyReturnsToSearchWithMessage() throws Exception {
        when(service.getBookingById(11L)).thenReturn(Booking.builder().passenger(User.builder().email("anfas@example.test").build()).build());
        when(service.getCheckoutRecovery(11L)).thenReturn(new CheckoutRecovery(false, 0, null,
                "This departure has already passed. Please search another train."));
        mvc.perform(get("/booking/11/seats-again").principal(() -> "anfas@example.test")).andExpect(redirectedUrl("/trains/search"))
                .andExpect(flash().attribute("errorMessage", "This departure has already passed. Please search another train."));
        verify(service, never()).createBooking(any(), any());
    }
}
