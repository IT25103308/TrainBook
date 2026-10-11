package com.trainbooking.it25102925.controller;
import com.trainbooking.exception.ResourceNotFoundException;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25102327.service.TrainService;
import com.trainbooking.it25102925.dto.ServiceStatusRequest;
import com.trainbooking.it25102925.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.*;

@Controller @RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final TrainService trainService;
    private final UserRepository userRepository;
    private final ServiceStatusService statusService;
    @InitBinder("statusRequest") void bindStatus(org.springframework.web.bind.WebDataBinder binder){binder.setAllowedFields("scheduleId","serviceDate","status","delayMinutes","reason");}
    @GetMapping("/notifications") public String showNotifications(Authentication authentication,@RequestParam(defaultValue="ALL")String filter,Model model,RedirectAttributes flash) {
        if(!java.util.Set.of("ALL","UNREAD","ARCHIVED").contains(filter.toUpperCase(java.util.Locale.ROOT))){flash.addFlashAttribute("errorMessage","Choose All, Unread or Archived alerts.");return "redirect:/notifications";}
        model.addAttribute("notifications",notificationService.list(resolveUserId(authentication),filter));model.addAttribute("selectedFilter",filter.toUpperCase(java.util.Locale.ROOT));return "it25102925/list";
    }
    @PostMapping("/notifications/mark-all-read") public String markAllAsRead(Authentication auth){notificationService.markAllAsRead(resolveUserId(auth));return "redirect:/notifications";}
    @PostMapping("/notifications/{id}/read") public String markRead(@PathVariable Long id,Authentication auth){notificationService.markRead(id,resolveUserId(auth));return "redirect:/notifications";}
    @PostMapping("/notifications/{id}/archive") public String archive(@PathVariable Long id,Authentication auth){notificationService.archive(id,resolveUserId(auth),true);return "redirect:/notifications";}
    @PostMapping("/notifications/{id}/restore") public String restore(@PathVariable Long id,Authentication auth){notificationService.archive(id,resolveUserId(auth),false);return "redirect:/notifications?filter=ARCHIVED";}
    private Long resolveUserId(Authentication auth){if(auth==null || !auth.isAuthenticated())throw new ResourceNotFoundException("Account not found.");return userRepository.findByEmail(auth.getName()).map(com.trainbooking.it25101520.model.User::getId).orElseThrow(()->new ResourceNotFoundException("Account not found."));}
    @GetMapping({"/admin/trains/status","/notifications/status"}) public String showStatusUpdateForm(
            @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)LocalDate date,Model model) {
        LocalDate selected=date==null?LocalDate.now():date;model.addAttribute("services",statusService.board(selected));model.addAttribute("serviceDateIso",selected.toString());model.addAttribute("changes",statusService.recentChanges());
        if(!model.containsAttribute("statusRequest")){var form=new ServiceStatusRequest();form.setServiceDate(selected);form.setStatus("ON_TIME");form.setDelayMinutes(0);model.addAttribute("statusRequest",form);}return "it25102925/status";
    }
    @PostMapping({"/admin/trains/status","/notifications/status"}) public String updateTrainStatusAndNotify(@ModelAttribute("statusRequest")ServiceStatusRequest request,BindingResult errors,Authentication auth,RedirectAttributes flash) {
        try {if(errors.hasErrors())throw new IllegalArgumentException("Enter a valid service date and whole delay minutes.");statusService.update(request,auth.getName());flash.addFlashAttribute("successMessage","Dated service status saved. Alerts use the committed service/date and passenger preferences.");}
        catch(IllegalArgumentException|IllegalStateException invalid){flash.addFlashAttribute("statusRequest",request);flash.addFlashAttribute("errorMessage",invalid.getMessage());}
        if(request.getServiceDate()!=null)flash.addAttribute("date",request.getServiceDate().toString());return "redirect:/notifications/status";
    }
    @PostMapping("/notifications/status/{id}/delete") public String deleteStatus(@PathVariable Long id,Authentication auth,RedirectAttributes flash){
        try {statusService.deleteUnused(id,auth.getName());flash.addFlashAttribute("successMessage","Unused dated status removed; its change audit is retained.");}
        catch(IllegalStateException invalid){flash.addFlashAttribute("errorMessage",invalid.getMessage());}return "redirect:/notifications/status";
    }
    @GetMapping("/departure-board") public String showDepartureBoard(@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)LocalDate date,Model model){
        LocalDate selected=date==null?LocalDate.now():date;model.addAttribute("services",statusService.board(selected));model.addAttribute("serviceDateIso",selected.toString());return "it25102925/departure-board";
    }
    @GetMapping({"/notifications/map","/notifications/live-map","/trains/live-map"}) public String showLiveMap(@RequestParam(required=false)String view,Authentication auth,Model model){
        model.addAttribute("pageTitle","Reported Train Positions");model.addAttribute("trains",trainService.getAllTrains());
        boolean privileged=auth!=null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a->java.util.Set.of("ROLE_ADMIN","ROLE_COORDINATOR","ROLE_STATION_STAFF").contains(a.getAuthority()));
        return privileged && !"passenger".equalsIgnoreCase(view) && !"user".equalsIgnoreCase(view)?"it25102925/admin-live-map":"it25102925/live-map";
    }
    @GetMapping({"/admin/live-map","/admin/trains/map"}) public String showAdminLiveMap(Model model){model.addAttribute("pageTitle","Reported Train Positions");model.addAttribute("trains",trainService.getAllTrains());return "it25102925/admin-live-map";}
}
