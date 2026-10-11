package com.trainbooking.it25102925.controller;
import com.trainbooking.it25102925.dto.TelemetryForm;
import com.trainbooking.it25102925.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;
import java.time.LocalDate;

@Controller @RequiredArgsConstructor
public class TelemetryController {
    private final ServiceStatusService statuses;
    private final TrainLocationService locations;
    @InitBinder("telemetry") void binder(org.springframework.web.bind.WebDataBinder binder){binder.setAllowedFields("scheduleId","serviceDate","latitude","longitude","speed","expectedArrival","simulated");}
    @GetMapping("/notifications/telemetry") public String show(@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)LocalDate date,Model model){
        LocalDate selected=date==null?LocalDate.now():date;model.addAttribute("services",statuses.board(selected));model.addAttribute("positions",locations.getAllLocations());model.addAttribute("serviceDateIso",selected.toString());
        if(!model.containsAttribute("telemetry")){var form=new TelemetryForm();form.setServiceDate(selected);form.setSpeed(0.0);model.addAttribute("telemetry",form);}return "it25102925/telemetry";
    }
    @PostMapping("/notifications/telemetry") public String report(@ModelAttribute("telemetry")TelemetryForm form,BindingResult result,Principal principal,RedirectAttributes flash){
        try {if(result.hasErrors())throw new IllegalArgumentException("Enter valid numeric coordinates/speed and a valid local date/time.");
            var row=statuses.board(form.getServiceDate()).stream().filter(s->s.scheduleId().equals(form.getScheduleId())).findFirst().orElseThrow(()->new IllegalArgumentException("Choose a configured departure on this date."));
            locations.report(row.trainId(),row.scheduleId(),form.getServiceDate(),form.getLatitude(),form.getLongitude(),form.getSpeed(),null,null,null,"OUTBOUND",null,null,null,form.getExpectedArrival(),form.isSimulated(),principal.getName());
            flash.addFlashAttribute("successMessage","Reported position saved. A supplied expected arrival is checked against this service's planned arrival; eligible deviation alerts use passenger preferences.");
        } catch(IllegalArgumentException|IllegalStateException invalid){flash.addFlashAttribute("telemetry",form);flash.addFlashAttribute("errorMessage",invalid.getMessage());}
        return "redirect:/notifications/telemetry";
    }
    @PostMapping("/notifications/telemetry/{id}/clear") public String clear(@PathVariable Long id,RedirectAttributes flash){locations.clear(id);flash.addFlashAttribute("successMessage","Latest reported position cleared. No invented location is substituted.");return "redirect:/notifications/telemetry";}
}
