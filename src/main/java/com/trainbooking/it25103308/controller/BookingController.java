package com.trainbooking.it25103308.controller;

import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.service.PassengerService;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.repository.ScheduleRepository;
import com.trainbooking.it25103308.dto.BookingRequest;
import com.trainbooking.it25103308.dto.SeatAvailability;
import com.trainbooking.it25103308.dto.CheckoutHold;
import com.trainbooking.it25103308.dto.CheckoutRecovery;
import com.trainbooking.it25103308.model.Booking;
import com.trainbooking.it25103308.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller managing booking workflows, seat selection, and payment transitions.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final ScheduleRepository scheduleRepository;
    private final PassengerService passengerService;

    @InitBinder("bookingRequest")
    void bindReservation(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("scheduleId", "travelDate", "seatClass", "numberOfSeats", "seatCount", "seatNumbers",
                "passengerName", "documentType", "passengerNic", "contactPhone");
    }

    @InitBinder("bookingDetails")
    void bindDetails(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("passengerName", "documentType", "passengerNic", "contactPhone");
    }

    /**
     * Displays seat selection view for a chosen schedule and travel date.
     *
     * @param scheduleId schedule ID
     * @param date date of travel
     * @param model UI model
     * @return 'it25103308/seat-select' template name
     */
    @GetMapping("/booking/seats")
    public String showSeatSelection(
            @RequestParam("scheduleId") Long scheduleId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "seatClass", defaultValue = "") String seatClass,
            Model model, Principal principal
    ) {
        log.debug("Showing seat selection for schedule ID: {}, date: {}", scheduleId, date);
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with ID: " + scheduleId));

        SeatAvailability first = bookingService.getSeatAvailability(scheduleId, date, "FIRST");
        SeatAvailability second = bookingService.getSeatAvailability(scheduleId, date, "SECOND");
        int availFirst = first.availableSeats();
        int availSecond = second.availableSeats();

        Map<String, Object> schedView = new HashMap<>();
        schedView.put("id", schedule.getId());
        schedView.put("trainName", schedule.getTrain() != null ? schedule.getTrain().getTrainName() : "Express Train");
        schedView.put("trainType", schedule.getTrain() != null && schedule.getTrain().getTrainType() != null ? schedule.getTrain().getTrainType() : "Not recorded");
        schedView.put("origin", schedule.getRoute() != null ? schedule.getRoute().getOrigin() : "Not recorded");
        schedView.put("destination", schedule.getRoute() != null ? schedule.getRoute().getDestination() : "Not recorded");
        var plan=com.trainbooking.it25102327.service.ServicePlan.resolve(schedule,date);
        var firstFare=quotedFare(plan,"FIRST");var secondFare=quotedFare(plan,"SECOND");
        boolean serviceAvailable=plan.arrival()!=null && plan.availableAt(LocalDateTime.now());
        if(!serviceAvailable || firstFare==null) availFirst=0;
        if(!serviceAvailable || secondFare==null) availSecond=0;
        model.addAttribute("journeyUnavailable",!serviceAvailable);
        schedView.put("departureTime", plan.departure());
        schedView.put("arrivalTime", plan.arrival());
        schedView.put("arrivalDayOffset",plan.arrivalDayOffset());
        schedView.put("departurePlatform",plan.departurePlatform());
        schedView.put("arrivalPlatform",plan.arrivalPlatform());
        model.addAttribute("journeyStatus",plan.status());
        model.addAttribute("journeyDelay",plan.delayMinutes());
        model.addAttribute("journeyStops",plan.stops());
        model.addAttribute("journeyLabel",plan.label());
        schedView.put("firstClassFare", firstFare);
        schedView.put("secondClassFare", secondFare);
        schedView.put("availableFirstClassSeats", availFirst);
        schedView.put("availableSecondClassSeats", availSecond);

        model.addAttribute("schedule", schedView);
        model.addAttribute("canReserve", principal != null);
        model.addAttribute("scheduleId", scheduleId);
        model.addAttribute("date", date);
        // URL/form values must stay ISO even when the page uses a local date display format.
        model.addAttribute("travelDateIso", date.toString());
        model.addAttribute("firstClassAvailable", availFirst);
        model.addAttribute("secondClassAvailable", availSecond);
        model.addAttribute("firstClassCapacity", first.capacity());
        model.addAttribute("secondClassCapacity", second.capacity());
        model.addAttribute("bookedFirstSeats", first.occupiedSeats());
        model.addAttribute("bookedSecondSeats", second.occupiedSeats());
        model.addAttribute("firstClassAllocationWarning",!serviceAvailable ? "This departure is unavailable. Please search another service." : firstFare==null ? "This class fare needs staff review. Choose another available class." : first.warning());
        model.addAttribute("secondClassAllocationWarning",!serviceAvailable ? "This departure is unavailable. Please search another service." : secondFare==null ? "This class fare needs staff review. Choose another available class." : second.warning());
        if (!model.containsAttribute("bookingRequest")) {
            String selectedClass = seatClass.isBlank() ? (availFirst > 0 ? "FIRST" : "SECOND")
                    : seatClass.trim().toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("FIRST", "SECOND").contains(selectedClass)) {
                throw new com.trainbooking.exception.ApiException(400, "Please choose FIRST or SECOND class.");
            }
            model.addAttribute("bookingRequest", new BookingRequest(scheduleId, date, selectedClass, 1));
        }

        return "it25103308/seat-select";
    }

    private java.math.BigDecimal quotedFare(com.trainbooking.it25102327.service.ServicePlan plan,String seatClass) {
        try { return plan.fare(seatClass); } catch(IllegalArgumentException invalid) { return null; }
    }

    /** Protected GET continuation: signing in never replays a reservation POST or creates a hold. */
    @GetMapping("/booking/sign-in")
    public String continueAfterSignIn(@RequestParam("scheduleId") Long scheduleId,
            @RequestParam("date") String dateText,
            @RequestParam(value="seatClass",defaultValue="FIRST") String seatClass,
            Principal principal, RedirectAttributes redirect) {
        if (principal == null) return "redirect:/login";
        LocalDate date;
        try {
            date = LocalDate.parse(dateText);
        } catch (java.time.format.DateTimeParseException invalidLink) {
            redirect.addFlashAttribute("searchError","This journey link is invalid or outdated. Please search again to choose your travel date.");
            return "redirect:/trains/search";
        }
        if (scheduleId < 1 || date.isBefore(LocalDate.now()) || !java.util.Set.of("FIRST","SECOND").contains(seatClass)) {
            redirect.addFlashAttribute("searchError","Choose a valid journey and class to continue.");
            return "redirect:/trains/search";
        }
        redirect.addAttribute("scheduleId",scheduleId);redirect.addAttribute("date",date.toString());redirect.addAttribute("seatClass",seatClass);
        return "redirect:/booking/seats";
    }

    /**
     * Handles new booking submission and redirects to checkout/payment page.
     *
     * @param principal authenticated user
     * @param bookingRequest booking form payload
     * @return redirect to payment view
     */
    @PostMapping("/booking")
    public String createBooking(
            Principal principal,
            @ModelAttribute("bookingRequest") BookingRequest bookingRequest,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (principal == null) {
            return "redirect:/login";
        }
        try {
            if (bindingResult.hasErrors()) {
                throw new IllegalArgumentException("Please enter a valid journey date and seat quantity.");
            }
            User user = passengerService.getUserByEmail(principal.getName());
            Booking booking = bookingService.createBooking(bookingRequest, user.getId());
            return "redirect:/booking/" + booking.getId() + "/payment";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("bookingError", ex.getMessage());
            if (bookingRequest.getScheduleId() == null || bookingRequest.getScheduleId() < 1
                    || bookingRequest.getTravelDate() == null) {
                return "redirect:/trains/search";
            }
            // Keep contact details, but let the refreshed map choose a valid quantity.
            Integer quantity;
            try {
                quantity = bookingRequest.getNumberOfSeats();
            } catch (IllegalArgumentException invalidQuantity) {
                quantity = 1;
            }
            quantity = quantity == null || quantity < 1 || quantity > 6 ? 1 : quantity;
            bookingRequest.setNumberOfSeats(quantity);
            bookingRequest.setSeatCount(quantity);
            bookingRequest.setSeatNumbers(null);
            redirectAttributes.addFlashAttribute("bookingRequest", bookingRequest);
            redirectAttributes.addAttribute("scheduleId", bookingRequest.getScheduleId());
            redirectAttributes.addAttribute("date", bookingRequest.getTravelDate().toString());
            return "redirect:/booking/seats";
        }
    }

    /**
     * Displays payment view for a specific booking.
     *
     * @param id booking ID
     * @param model UI model
     * @return 'it25100977/payment' template name
     */
    @GetMapping({"/booking/{id}/payment", "/payment/checkout/{id}"})
    public String showPaymentPage(@PathVariable("id") Long id, Principal principal, Model model) {
        log.debug("Displaying payment page for booking ID: {}", id);
        if (principal == null) return "redirect:/login";
        Booking booking = bookingService.getBookingById(id);
        com.trainbooking.it25103308.service.BookingAccess.requireOwner(booking, principal);
        model.addAttribute("booking", booking);
        model.addAttribute("checkoutPage", true);
        CheckoutHold hold = CheckoutHold.from(booking, LocalDateTime.now());
        model.addAttribute("checkoutHold", hold);
        model.addAttribute("checkoutRecovery", bookingService.getCheckoutRecovery(id));
        model.addAttribute("paymentAttempt", bookingService.getPaymentAttempt(id));
        return "it25100977/payment";
    }

    @GetMapping("/booking/{id}/details")
    public String editDetails(@PathVariable Long id, Principal principal, Model model,
                              RedirectAttributes redirect) {
        if (principal == null) return "redirect:/login";
        Booking booking = bookingService.getBookingById(id);
        com.trainbooking.it25103308.service.BookingAccess.requireOwner(booking, principal);
        CheckoutHold hold = CheckoutHold.from(booking, LocalDateTime.now());
        if (!hold.isPayable()) {
            redirect.addFlashAttribute("errorMessage", "Passenger details can only be edited while the unpaid hold is active.");
            return "redirect:/booking/" + id + "/payment";
        }
        model.addAttribute("booking", booking);
        model.addAttribute("checkoutHold", hold);
        if (!model.containsAttribute("bookingDetails")) model.addAttribute("bookingDetails",
                com.trainbooking.it25103308.dto.BookingDetailsRequest.from(booking));
        return "it25103308/booking-details";
    }

    @PostMapping("/booking/{id}/details")
    public String updateDetails(@PathVariable Long id, Principal principal,
            @ModelAttribute("bookingDetails") com.trainbooking.it25103308.dto.BookingDetailsRequest request,
            BindingResult result, RedirectAttributes redirect) {
        if (principal == null) return "redirect:/login";
        try {
            if (result.hasErrors()) throw new IllegalArgumentException("Check the passenger details and try again.");
            bookingService.updatePassengerDetails(id, principal.getName(), request);
            redirect.addFlashAttribute("successMessage", "Passenger details saved. Your seats, fare and original hold deadline are unchanged.");
            return "redirect:/booking/" + id + "/payment";
        } catch (IllegalArgumentException invalid) {
            redirect.addFlashAttribute("bookingDetails", request);
            redirect.addFlashAttribute("detailsError", invalid.getMessage());
            return "redirect:/booking/" + id + "/details";
        } catch (IllegalStateException unavailable) {
            redirect.addFlashAttribute("errorMessage", unavailable.getMessage());
            return "redirect:/booking/" + id + "/payment";
        }
    }

    /** Recheck a potentially stale recovery link before opening a fresh seat map. */
    @GetMapping("/booking/{id}/seats-again")
    public String selectSeatsAgain(@PathVariable("id") Long id, Principal principal, RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        com.trainbooking.it25103308.service.BookingAccess.requireOwner(bookingService.getBookingById(id), principal);
        CheckoutRecovery recovery = bookingService.getCheckoutRecovery(id);
        if (!recovery.canSelectAgain()) {
            redirectAttributes.addFlashAttribute("errorMessage", recovery.message());
            return "redirect:/trains/search";
        }
        return "redirect:" + recovery.selectionUrl();
    }

    /**
     * Handles explicit seat lock cancellation from the passenger.
     *
     * @param id booking ID
     * @return redirect back to train search
     */
    @PostMapping({"/booking/{id}/cancel", "/booking/release"})
    public String cancelBooking(@PathVariable(value = "id", required = false) Long id,
                                @org.springframework.web.bind.annotation.RequestParam(value = "bookingId", required = false) Long bookingId, Principal principal,
                                RedirectAttributes redirect) {
        if (principal == null) return "redirect:/login";
        Long targetId = (id != null) ? id : bookingId;
        if (targetId == null) throw new ResourceNotFoundException("Reservation not found.");
        {
            com.trainbooking.it25103308.service.BookingAccess.requireOwner(bookingService.getBookingById(targetId), principal);
            log.info("Passenger requested explicit cancellation for booking #{}", targetId);
            try {
                var reason = bookingService.cancelPendingBooking(targetId, principal.getName());
                redirect.addFlashAttribute("successMessage", reason == Booking.CancellationReason.HOLD_EXPIRED
                        ? "The hold expired and its seats are available again." : "This reservation is cancelled. Its seats are no longer held.");
            } catch (IllegalStateException paid) {
                redirect.addFlashAttribute("errorMessage", paid.getMessage());
                return "redirect:/booking/" + targetId + "/payment";
            }
        }
        return "redirect:/passenger/bookings";
    }
}
