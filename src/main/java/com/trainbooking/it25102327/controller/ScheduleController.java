package com.trainbooking.it25102327.controller;
import com.trainbooking.it25102327.dto.*;
import com.trainbooking.it25102327.service.*;
import com.trainbooking.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor
public class ScheduleController {
    private final ScheduleService scheduleService;
    private final TrainService trainService;
    @InitBinder("scheduleDto") void fields(org.springframework.web.bind.WebDataBinder binder) {
        binder.setAllowedFields("trainId","routeId","departureTime","arrivalTime","arrivalDayOffset","dayOfWeek","firstClassFare","secondClassFare","farePolicy","isActive","departurePlatform","arrivalPlatform",
            "stops[*].station","stops[*].arrivalTime","stops[*].departureTime","stops[*].dayOffset","stops[*].platform");binder.setAutoGrowCollectionLimit(20);
    }
    @InitBinder("overrideDto") void overrideFields(org.springframework.web.bind.WebDataBinder binder) { binder.setAllowedFields("serviceDate","kind","label","departureTime","arrivalTime","arrivalDayOffset","firstClassFare","secondClassFare"); }
    private void errors(BindingResult result,IllegalArgumentException invalid) {
        if(invalid instanceof OperationValidation fields) fields.fields().forEach((field,message)->result.rejectValue(field,"invalid",message));else result.reject("invalid",invalid.getMessage());
    }
    private String page(Model model,ScheduleDto dto,Long id) {
        model.addAttribute("schedules",scheduleService.getAllSchedules());model.addAttribute("trains",trainService.getAllTrains());model.addAttribute("routes",scheduleService.getAllRoutes());
        if(dto.getStops()==null) dto.setStops(new java.util.ArrayList<>());if(dto.getStops().isEmpty()) dto.getStops().add(new StopDto());
        model.addAttribute("scheduleDto",dto);model.addAttribute("editId",id);model.addAttribute("weekdays",java.time.DayOfWeek.values());model.addAttribute("pageTitle","Timetables and service dates");return "it25102327/schedules";
    }
    @GetMapping({"/trains/schedules","/schedules"}) public String listSchedules(Model model) {
        ScheduleDto dto=new ScheduleDto();dto.setArrivalDayOffset(0);dto.setFarePolicy("FIXED");dto.setIsActive(true);return page(model,dto,null);
    }
    @GetMapping("/schedules/{id}/edit") public String edit(@PathVariable("id") Long id,Model model,RedirectAttributes flash) {
        try { return page(model,scheduleService.getScheduleDto(id),id); }catch(ResourceNotFoundException missing) { flash.addFlashAttribute("errorMessage",missing.getMessage());return "redirect:/trains/schedules"; }
    }
    @PostMapping("/schedules") public String createSchedule(@ModelAttribute("scheduleDto") ScheduleDto dto,BindingResult result,Model model,RedirectAttributes flash) {
        if(!result.hasErrors()) try { scheduleService.createSchedule(dto);flash.addFlashAttribute("successMessage","Timetable saved.");return "redirect:/trains/schedules"; }catch(IllegalArgumentException invalid) { errors(result,invalid); }
        return page(model,dto,null);
    }
    @PostMapping("/schedules/{id}") public String updateSchedule(@PathVariable("id") Long id,@ModelAttribute("scheduleDto") ScheduleDto dto,BindingResult result,Model model,RedirectAttributes flash) {
        if(!result.hasErrors()) try { scheduleService.updateSchedule(id,dto);flash.addFlashAttribute("successMessage","Timetable updated. Existing reservation amounts are retained.");return "redirect:/trains/schedules"; }
        catch(IllegalArgumentException invalid) { errors(result,invalid); }catch(ResourceNotFoundException missing) { flash.addFlashAttribute("errorMessage",missing.getMessage());return "redirect:/trains/schedules"; }
        return page(model,dto,id);
    }
    @PostMapping("/schedules/{id}/delete") public String deleteSchedule(@PathVariable("id") Long id,RedirectAttributes flash) {
        try { scheduleService.deleteSchedule(id);flash.addFlashAttribute("successMessage","Unused timetable deleted."); }catch(IllegalArgumentException|ResourceNotFoundException rejected) { flash.addFlashAttribute("errorMessage",rejected.getMessage()); }
        return "redirect:/trains/schedules";
    }
    @PostMapping("/schedules/maintenance/block") public String maintenance(@RequestParam("scheduleId") Long id,@RequestParam(value="blocked",defaultValue="false") boolean blocked,@RequestParam(value="notes",required=false) String notes,RedirectAttributes flash) {
        try { String message=scheduleService.setMaintenanceBlock(id,blocked,notes);flash.addFlashAttribute(message.startsWith("WARNING") ? "errorMessage" : "successMessage",message); }
        catch(IllegalArgumentException|ResourceNotFoundException rejected) { flash.addFlashAttribute("errorMessage",rejected.getMessage()); }return "redirect:/trains/schedules";
    }
    private String overridePage(Long id,Long overrideId,OverrideDto dto,Model model) {
        model.addAttribute("schedule",scheduleService.getScheduleDto(id));model.addAttribute("overrides",scheduleService.getOverrides(id));model.addAttribute("overrideDto",dto);
        model.addAttribute("overrideId",overrideId);model.addAttribute("today",java.time.LocalDate.now().toString());model.addAttribute("pageTitle","Dated service changes");return "it25102327/service-dates";
    }
    @GetMapping("/schedules/{id}/dates") public String dates(@PathVariable("id") Long id,Model model,RedirectAttributes flash) {
        try { OverrideDto dto=new OverrideDto();dto.setArrivalDayOffset(0);return overridePage(id,null,dto,model); }catch(ResourceNotFoundException missing) { flash.addFlashAttribute("errorMessage",missing.getMessage());return "redirect:/trains/schedules"; }
    }
    @GetMapping("/schedules/{id}/quote") @ResponseBody
    public java.util.Map<String,Object> quote(@PathVariable("id") Long id,@RequestParam("date") @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,@RequestParam("seatClass") String seatClass) {
        try { return scheduleService.quote(id,date,seatClass); }
        catch(IllegalArgumentException invalid) { throw new com.trainbooking.exception.ApiException(400,invalid.getMessage()); }
    }
    @GetMapping("/schedules/{id}/dates/{change}/edit") public String editDate(@PathVariable("id") Long id,@PathVariable("change") Long change,Model model,RedirectAttributes flash) {
        try { return overridePage(id,change,scheduleService.getOverrideDto(id,change),model); }catch(ResourceNotFoundException missing) { flash.addFlashAttribute("errorMessage",missing.getMessage());return "redirect:/schedules/"+id+"/dates"; }
    }
    @PostMapping({"/schedules/{id}/dates","/schedules/{id}/dates/{change}"}) public String saveDate(@PathVariable("id") Long id,@PathVariable(value="change",required=false) Long change,@ModelAttribute("overrideDto") OverrideDto dto,BindingResult result,Model model,RedirectAttributes flash) {
        if(!result.hasErrors()) try { String message=scheduleService.saveOverride(id,change,dto);flash.addFlashAttribute(message.startsWith("WARNING") ? "errorMessage" : "successMessage",message);return "redirect:/schedules/"+id+"/dates"; }
        catch(IllegalArgumentException invalid) { errors(result,invalid); }catch(ResourceNotFoundException missing) { flash.addFlashAttribute("errorMessage",missing.getMessage());return "redirect:/trains/schedules"; }
        return overridePage(id,change,dto,model);
    }
    @PostMapping("/schedules/{id}/dates/{change}/delete") public String deleteDate(@PathVariable("id") Long id,@PathVariable("change") Long change,RedirectAttributes flash) {
        try { scheduleService.deleteOverride(id,change);flash.addFlashAttribute("successMessage","Dated change removed. Current recurring eligibility is restored."); }
        catch(IllegalArgumentException|ResourceNotFoundException rejected) { flash.addFlashAttribute("errorMessage",rejected.getMessage()); }return "redirect:/schedules/"+id+"/dates";
    }
}
