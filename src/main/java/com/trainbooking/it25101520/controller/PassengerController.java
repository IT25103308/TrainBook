package com.trainbooking.it25101520.controller;

import com.trainbooking.it25101520.dto.FavoriteRouteDto;
import com.trainbooking.it25101520.dto.UserProfileDto;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.service.*;
import com.trainbooking.it25103308.repository.BookingRepository;
import com.trainbooking.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class PassengerController {
    private final PassengerService passengerService;
    private final BookingRepository bookingRepository;
    private final BookingHistoryService bookingHistoryService;

    @InitBinder("profileForm")
    public void profileFields(WebDataBinder binder) {
        binder.setAllowedFields("firstName","lastName","phone","notifyByEmail","notifyBySms","delayAlertThresholdMinutes",
                "_notifyByEmail","_notifyBySms");
    }

    private User currentUser(Principal principal) {
        return principal == null ? null : passengerService.getUserByEmail(principal.getName());
    }

    private void profileModel(User user, Model model) {
        Map<String,Object> view = new HashMap<>();
        view.put("fullName",user.getFirstName()+" "+user.getLastName()); view.put("email",user.getEmail());
        view.put("memberSince",user.getCreatedAt()==null ? "Not recorded" : user.getCreatedAt().format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        view.put("totalBookings",bookingRepository.findByPassengerId(user.getId()).size());
        model.addAttribute("user",view);
        if (!model.containsAttribute("profileForm")) model.addAttribute("profileForm",passengerService.getUserProfile(user.getId()));
        if (!model.containsAttribute("favoriteForm")) model.addAttribute("favoriteForm",new FavoriteRouteDto());
        model.addAttribute("favoriteRoutes",passengerService.getFavoriteRoutes(user.getId()));
        model.addAttribute("stationNames",passengerService.getStationNames());
    }

    @GetMapping({"/profile","/passenger/profile","/favorites","/passenger/favorites"})
    public String showProfile(Principal principal, Model model) {
        User user=currentUser(principal); if(user==null) return "redirect:/login";
        profileModel(user,model); return "it25101520/profile";
    }

    @PostMapping({"/profile","/passenger/profile"})
    public String updateProfile(Principal principal, @ModelAttribute("profileForm") UserProfileDto form,
                                BindingResult errors, Model model, RedirectAttributes flash) {
        User user=currentUser(principal); if(user==null) return "redirect:/login";
        AccountFormRules.profile(form,errors);
        if(errors.hasErrors()) { profileModel(user,model); return "it25101520/profile"; }
        passengerService.updateProfile(user.getId(),form);
        flash.addFlashAttribute("successMessage","Profile and alert preferences saved."); return "redirect:/passenger/profile";
    }

    @PostMapping({"/favorites","/passenger/favorites/add"})
    public String addFavorite(Principal principal, @Valid @ModelAttribute("favoriteForm") FavoriteRouteDto form,
                              BindingResult errors, Model model, RedirectAttributes flash) {
        User user=currentUser(principal); if(user==null) return "redirect:/login";
        if(!errors.hasErrors()) {
            try { passengerService.addFavoriteRoute(user.getId(),form); }
            catch(IllegalArgumentException ex) { errors.reject("favorite.invalid",ex.getMessage()); }
        }
        if(errors.hasErrors()) { profileModel(user,model); return "it25101520/profile"; }
        flash.addFlashAttribute("successMessage","Favourite route saved."); return "redirect:/passenger/profile#favourites";
    }

    @GetMapping("/passenger/favorites/{id}/edit")
    public String editFavorite(Principal principal,@PathVariable Long id,Model model,RedirectAttributes flash) {
        User user=currentUser(principal); if(user==null) return "redirect:/login";
        try {
            var favorite=passengerService.getFavoriteRoute(id,user.getId());
            model.addAttribute("favoriteForm",FavoriteRouteDto.builder().origin(favorite.getOrigin()).destination(favorite.getDestination()).build());
            model.addAttribute("favoriteId",id); model.addAttribute("stationNames",passengerService.getStationNames());
            return "it25101520/favorite-edit";
        } catch(ResourceNotFoundException ex) {
            flash.addFlashAttribute("errorMessage","Saved route not found."); return "redirect:/passenger/profile#favourites";
        }
    }

    @PostMapping("/passenger/favorites/{id}/edit")
    public String updateFavorite(Principal principal,@PathVariable Long id,
            @Valid @ModelAttribute("favoriteForm") FavoriteRouteDto form,BindingResult errors,Model model,RedirectAttributes flash) {
        User user=currentUser(principal); if(user==null) return "redirect:/login";
        try {
            passengerService.getFavoriteRoute(id,user.getId());
            if(!errors.hasErrors()) {
                try { passengerService.updateFavoriteRoute(id,user.getId(),form); }
                catch(IllegalArgumentException ex) { errors.reject("favorite.invalid",ex.getMessage()); }
            }
        } catch(ResourceNotFoundException ex) {
            flash.addFlashAttribute("errorMessage","Saved route not found."); return "redirect:/passenger/profile#favourites";
        }
        if(errors.hasErrors()) {
            model.addAttribute("favoriteId",id); model.addAttribute("stationNames",passengerService.getStationNames());
            return "it25101520/favorite-edit";
        }
        flash.addFlashAttribute("successMessage","Favourite route updated."); return "redirect:/passenger/profile#favourites";
    }

    @PostMapping({"/favorites/{id}/delete","/passenger/favorites/{id}/delete"})
    public String deleteFavorite(Principal principal,@PathVariable Long id,RedirectAttributes flash) {
        User user=currentUser(principal); if(user==null) return "redirect:/login";
        try { passengerService.removeFavoriteRoute(id,user.getId()); flash.addFlashAttribute("successMessage","Favourite route removed."); }
        catch(ResourceNotFoundException ex) { flash.addFlashAttribute("errorMessage","Saved route not found."); }
        return "redirect:/passenger/profile#favourites";
    }

    @GetMapping({"/booking-history","/passenger/bookings"})
    public String showBookingHistory(@RequestParam(value="filter",required=false) String filter,Principal principal,Model model) {
        User user=currentUser(principal); if(user==null) return "redirect:/login";
        String selected=BookingHistoryService.normalizeFilter(filter);
        model.addAttribute("bookings",bookingHistoryService.getBookings(user.getId(),selected));
        model.addAttribute("selectedFilter",selected); return "it25101520/booking-history";
    }
}
