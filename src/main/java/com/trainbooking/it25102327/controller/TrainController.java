package com.trainbooking.it25102327.controller;

import com.trainbooking.it25102327.dto.ScheduleDto;
import com.trainbooking.it25102327.dto.TrainDto;
import com.trainbooking.it25102327.dto.TrainSearchResultDto;
import com.trainbooking.it25102327.model.Schedule;
import com.trainbooking.it25102327.service.ScheduleService;
import com.trainbooking.it25102327.service.TrainService;
import com.trainbooking.it25102925.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller handling train search views, fleet management, and live schedule operations.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class TrainController {

    private final TrainService trainService;
    private final ScheduleService scheduleService;
    private final NotificationService notificationService;

    /**
     * Handles train search queries and displays matching available trains.
     * Serves both the home root URL and /trains/search.
     *
     * @param origin departure station
     * @param destination destination station
     * @param date travel date
     * @param model UI model
     * @return 'it25102327/search' template name
     */
    @GetMapping({"/", "/trains/search"})
    public String searchTrains(
            @RequestParam(value = "origin", required = false) String origin,
            @RequestParam(value = "destination", required = false) String destination,
            @RequestParam(value = "date", required = false) String rawDate,
            @RequestParam(value = "seatClass", required = false) String seatClass,
            Model model
    ) {
        model.addAttribute("stations", trainService.getStations());
        model.addAttribute("origin", origin == null ? "" : origin.trim());
        model.addAttribute("destination", destination == null ? "" : destination.trim());
        model.addAttribute("seatClass", seatClass == null ? "" : seatClass.trim().toUpperCase(java.util.Locale.ROOT));
        model.addAttribute("today", LocalDate.now());
        LocalDate date = LocalDate.now();
        boolean submitted = origin != null || destination != null || rawDate != null || seatClass != null;
        if (submitted) {
            try {
                if (rawDate != null) {
                    if (rawDate.isBlank()) throw new IllegalArgumentException("Choose a travel date.");
                    try { date = LocalDate.parse(rawDate); }
                    catch (java.time.format.DateTimeParseException invalidDate) {
                        throw new IllegalArgumentException("Choose a valid travel date.");
                    }
                }
                model.addAttribute("trainResults", trainService.searchTrains(origin, destination, date, seatClass));
            } catch (IllegalArgumentException invalidSearch) {
                model.addAttribute("searchError", invalidSearch.getMessage());
            }
        }
        model.addAttribute("date", date);
        // Real configured routes replace demonstration-only routes and invented fares.
        model.addAttribute("configuredRoutes", scheduleService.getAllRoutes().stream()
                .filter(route -> route.getOrigin() != null && route.getDestination() != null)
                .sorted(java.util.Comparator.comparing(com.trainbooking.it25102327.model.Route::getOrigin)
                        .thenComparing(com.trainbooking.it25102327.model.Route::getDestination))
                .limit(6).toList());
        return "it25102327/search";
    }

    @org.springframework.web.bind.annotation.InitBinder("trainDto")
    void trainFields(org.springframework.web.bind.WebDataBinder binder) { binder.setAllowedFields("trainNumber","trainName","trainType","firstClassSeats","secondClassSeats","status"); }
    @org.springframework.web.bind.annotation.InitBinder("routeDto")
    void routeFields(org.springframework.web.bind.WebDataBinder binder) { binder.setAllowedFields("origin","destination","distanceKm","defaultPlatform","destinationPlatform"); }
    private void errors(org.springframework.validation.BindingResult result,IllegalArgumentException invalid) {
        if(invalid instanceof com.trainbooking.it25102327.service.OperationValidation fields) fields.fields().forEach((field,message)->result.rejectValue(field,"invalid",message));
        else result.reject("invalid",invalid.getMessage());
    }
    private String fleet(Model model,TrainDto dto,Long editId) {
        model.addAttribute("trains",trainService.getAllTrains());model.addAttribute("trainDto",dto);model.addAttribute("editId",editId);
        model.addAttribute("trainTypes",List.of("Express Intercity","Intercity","Commuter","Night Mail","Special Service","Commuter / Slow","LUXURY_TOURIST"));
        model.addAttribute("statuses",List.of("ON_TIME","DELAYED","CANCELLED","MAINTENANCE","INACTIVE"));model.addAttribute("pageTitle","Fleet management");
        return "it25102327/fleet";
    }
    @GetMapping({"/trains/fleet","/trains/manage"}) public String fleetDashboard(Model model) { return fleet(model,new TrainDto(),null); }
    @GetMapping("/trains/{id}/edit") public String editTrainForm(@PathVariable("id") Long id,Model model,RedirectAttributes flash) {
        try {
            var t=trainService.getTrainById(id);var dto=TrainDto.builder().trainNumber(t.getTrainNumber()).trainName(t.getTrainName()).trainType(t.getTrainType())
                .firstClassSeats(t.getFirstClassSeats()).secondClassSeats(t.getSecondClassSeats()).status(t.getStatus()==null || t.getStatus().name().equals("ACTIVE") ? "ON_TIME" : t.getStatus().name()).build();
            return fleet(model,dto,id);
        } catch(com.trainbooking.exception.ResourceNotFoundException missing) { flash.addFlashAttribute("errorMessage",missing.getMessage());return "redirect:/trains/fleet"; }
    }
    @PostMapping({"/trains","/trains/add"}) public String createTrain(@ModelAttribute("trainDto") TrainDto dto,org.springframework.validation.BindingResult result,Model model,RedirectAttributes flash) {
        if(!result.hasErrors()) try { trainService.createTrain(dto);flash.addFlashAttribute("successMessage","Train saved.");return "redirect:/trains/fleet"; }
        catch(org.springframework.dao.DataIntegrityViolationException duplicate) { result.rejectValue("trainNumber","duplicate","This train number already exists."); }
        catch(IllegalArgumentException invalid) { errors(result,invalid); }
        return fleet(model,dto,null);
    }
    @PostMapping("/trains/{id}") public String updateTrain(@PathVariable("id") Long id,@ModelAttribute("trainDto") TrainDto dto,org.springframework.validation.BindingResult result,Model model,RedirectAttributes flash) {
        if(!result.hasErrors()) try {
            var previous=trainService.getTrainById(id).getStatus();var updated=trainService.updateTrain(id,dto);
            if(previous!=updated.getStatus()) dispatch(id,updated.getStatus().name());
            flash.addFlashAttribute("successMessage","Train updated. Existing reservation amounts remain unchanged.");return "redirect:/trains/fleet";
        } catch(org.springframework.dao.DataIntegrityViolationException duplicate) { result.rejectValue("trainNumber","duplicate","This train number already exists."); }
        catch(IllegalArgumentException invalid) { errors(result,invalid); }
        catch(com.trainbooking.exception.ResourceNotFoundException missing) { flash.addFlashAttribute("errorMessage",missing.getMessage());return "redirect:/trains/fleet"; }
        return fleet(model,dto,id);
    }
    // Non-HTTP convenience overload retained for focused callers/tests.
    public String updateTrain(Long id,TrainDto dto,RedirectAttributes flash) {
        return updateTrain(id,dto,new org.springframework.validation.BeanPropertyBindingResult(dto,"trainDto"),new org.springframework.ui.ExtendedModelMap(),flash);
    }
    private void dispatch(Long id,String status) {
        try { notificationService.notifyAffectedPassengers(id,status); } catch(Exception failure) { log.warn("Status saved; alert dispatch needs review: {}",failure.getMessage()); }
    }
    @PostMapping("/trains/{id}/delete") public String deleteTrain(@PathVariable("id") Long id,RedirectAttributes flash) {
        try { trainService.deleteTrain(id);flash.addFlashAttribute("successMessage","Unused train deleted."); }
        catch(IllegalArgumentException|com.trainbooking.exception.ResourceNotFoundException rejected) { flash.addFlashAttribute("errorMessage",rejected.getMessage()); }
        return "redirect:/trains/fleet";
    }
    @PostMapping("/trains/{id}/status") public String updateStatus(@PathVariable("id") Long id,@RequestParam("status") String status,RedirectAttributes flash) {
        try { var previous=trainService.getTrainById(id).getStatus();var updated=trainService.updateTrainStatus(id,status);if(previous!=updated.getStatus()) dispatch(id,updated.getStatus().name());flash.addFlashAttribute("successMessage","Train status updated."); }
        catch(IllegalArgumentException|com.trainbooking.exception.ResourceNotFoundException rejected) { flash.addFlashAttribute("errorMessage",rejected.getMessage()); }
        return "redirect:/trains/fleet";
    }
    private String routes(Model model,com.trainbooking.it25102327.dto.RouteDto dto,Long editId) {
        model.addAttribute("routes",scheduleService.getAllRoutes());model.addAttribute("routeDto",dto);model.addAttribute("editId",editId);
        model.addAttribute("stations",trainService.getStations());model.addAttribute("pageTitle","Routes and platforms");return "it25102327/routes";
    }
    @GetMapping("/trains/routes") public String routesDashboard(Model model) { var dto=new com.trainbooking.it25102327.dto.RouteDto();dto.setDefaultPlatform("Platform 1");dto.setDestinationPlatform("Platform 1");return routes(model,dto,null); }
    @GetMapping("/trains/routes/{id}/edit") public String routeEdit(@PathVariable("id") Long id,Model model,RedirectAttributes flash) {
        try { return routes(model,scheduleService.getRouteDto(id),id); }
        catch(com.trainbooking.exception.ResourceNotFoundException missing) { flash.addFlashAttribute("errorMessage",missing.getMessage());return "redirect:/trains/routes"; }
    }
    @PostMapping("/trains/routes/add") public String createRoute(@ModelAttribute("routeDto") com.trainbooking.it25102327.dto.RouteDto dto,org.springframework.validation.BindingResult result,Model model,RedirectAttributes flash) {
        if(!result.hasErrors()) try { scheduleService.createRoute(dto);flash.addFlashAttribute("successMessage","Route saved. Its stations are now available in search.");return "redirect:/trains/routes"; }
        catch(IllegalArgumentException invalid) { errors(result,invalid); }
        return routes(model,dto,null);
    }
    @PostMapping("/trains/routes/{id}") public String updateRoute(@PathVariable("id") Long id,@ModelAttribute("routeDto") com.trainbooking.it25102327.dto.RouteDto dto,org.springframework.validation.BindingResult result,Model model,RedirectAttributes flash) {
        if(!result.hasErrors()) try { scheduleService.updateRoute(id,dto);flash.addFlashAttribute("successMessage","Route updated.");return "redirect:/trains/routes"; }
        catch(IllegalArgumentException invalid) { errors(result,invalid); }
        catch(com.trainbooking.exception.ResourceNotFoundException missing) { flash.addFlashAttribute("errorMessage",missing.getMessage());return "redirect:/trains/routes"; }
        return routes(model,dto,id);
    }
    @PostMapping("/trains/routes/{id}/delete") public String deleteRoute(@PathVariable("id") Long id,RedirectAttributes flash) {
        try { scheduleService.deleteRoute(id);flash.addFlashAttribute("successMessage","Unused route deleted."); }
        catch(IllegalArgumentException|com.trainbooking.exception.ResourceNotFoundException rejected) { flash.addFlashAttribute("errorMessage",rejected.getMessage()); }
        return "redirect:/trains/routes";
    }

    /**
     * REST endpoint returning real-time train status, platform assignments, and capacities.
     * Used for auto-refreshing public departure boards and mobile apps.
     *
     * @return list of live train status objects
     */
    @GetMapping("/api/trains/live-status")
    @ResponseBody
    public List<Map<String, Object>> getLiveTrainStatus() {
        return trainService.getAllTrains().stream().map(train -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", train.getId());
            map.put("trainNumber", train.getTrainNumber());
            map.put("trainName", train.getTrainName());
            map.put("trainType", train.getTrainType() != null ? train.getTrainType() : "EXPRESS");
            map.put("status", train.getStatus() != null ? train.getStatus().name() : "ON_TIME");
            map.put("firstClassSeats", train.getFirstClassSeats());
            map.put("secondClassSeats", train.getSecondClassSeats());
            map.put("totalSeats", train.getTotalSeats());

            List<Schedule> schedules = scheduleService.getSchedulesByTrainId(train.getId());
            String platform = "Not assigned";
            String origin = "Not assigned";
            String destination = "Not assigned";
            if (!schedules.isEmpty()) {
                Schedule s = schedules.get(0);
                if (s.getRoute() != null && s.getRoute().getDefaultPlatform() != null && !s.getRoute().getDefaultPlatform().isBlank()) {
                    platform = s.getRoute().getDefaultPlatform();
                }
                if (s.getRoute() != null) {
                    origin = s.getRoute().getOrigin();
                    destination = s.getRoute().getDestination();
                }
            }
            map.put("platform", platform);
            map.put("origin", origin);
            map.put("destination", destination);
            return map;
        }).toList();
    }
}
