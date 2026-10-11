package com.trainbooking.it25102925.controller;
import com.trainbooking.it25102925.service.RebookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;
import java.time.LocalDate;

@Controller @RequiredArgsConstructor @RequestMapping("/rebooking")
public class RebookingController {
    private final RebookingService service;
    @GetMapping("/claim/{token}")
    public String showRebookingPortal(@PathVariable String token,Principal principal,Model model) {
        if(principal==null) return "redirect:/login";
        try {
            var offer=service.offer(token,principal.getName());model.addAttribute("booking",offer.getBooking());model.addAttribute("offer",offer);
            model.addAttribute("token",token);model.addAttribute("alternatives",service.alternatives(token,principal.getName()));
        } catch(IllegalArgumentException|IllegalStateException invalid){model.addAttribute("offerError",invalid.getMessage());}
        return "it25102925/rebook";
    }
    @PostMapping("/claim/{token}")
    public String confirmRebooking(@PathVariable String token,@RequestParam Long newScheduleId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate newTravelDate,
            Principal principal,RedirectAttributes flash) {
        if(principal==null) return "redirect:/login";
        try {var booking=service.transfer(token,newScheduleId,newTravelDate,principal.getName());
            flash.addFlashAttribute("successMessage","Replacement seats reserved. Your ticket now shows the new journey; the original paid amount is unchanged.");return "redirect:/booking/ticket/"+booking.getId();
        } catch(IllegalArgumentException|IllegalStateException invalid){flash.addFlashAttribute("errorMessage",invalid.getMessage());return "redirect:/rebooking/claim/"+token;}
    }
}
