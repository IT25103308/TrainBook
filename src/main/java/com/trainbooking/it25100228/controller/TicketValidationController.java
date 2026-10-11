package com.trainbooking.it25100228.controller;
import com.trainbooking.it25100228.dto.ValidationResultDto;
import com.trainbooking.it25100228.service.TicketValidationService;
import com.trainbooking.it25102925.service.ServiceStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;
import java.time.*;
import java.util.Map;
@Controller @RequiredArgsConstructor
public class TicketValidationController {
 private final TicketValidationService service;private final ServiceStatusService statuses;
 @GetMapping({"/validate","/dashboard/validate"}) public String showScannerPage(@RequestParam(required=false)Long scheduleId,@RequestParam(required=false)@org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)LocalDate date,Model model){LocalDate day=date==null?LocalDate.now():date;model.addAttribute("pageTitle","Station boarding & passenger manifest");model.addAttribute("services",statuses.board(day));model.addAttribute("serviceDateIso",day.toString());model.addAttribute("selectedScheduleId",scheduleId);model.addAttribute("recentTickets",service.manifest(scheduleId,day));return "it25100228/validate";}
 @PostMapping("/validate") @ResponseBody public ResponseEntity<ValidationResultDto> validateTicket(@RequestParam(required=false)String qrCode,@RequestParam(required=false)Long scheduleId,@RequestParam(required=false)@org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)LocalDate serviceDate,Principal actor){return ResponseEntity.ok(service.validateAndBoardTicket(qrCode,scheduleId,serviceDate,actor.getName()));}
 @PostMapping("/api/tickets/validate") @ResponseBody public ResponseEntity<ValidationResultDto> validateTicketApi(@RequestBody(required=false)Map<String,String> payload,Principal actor){Long id=null;LocalDate date=null;String code=null;try{if(payload!=null){code=payload.getOrDefault("ticketNumber",payload.get("qrCode"));id=payload.get("scheduleId")==null?null:Long.valueOf(payload.get("scheduleId"));date=payload.get("serviceDate")==null?null:LocalDate.parse(payload.get("serviceDate"));}}catch(NumberFormatException|DateTimeException ignored){}return ResponseEntity.ok(service.validateAndBoardTicket(code,id,date,actor.getName()));}
 @PostMapping({"/validate/{id}/reset","/dashboard/validate/{id}/reset"}) public String correct(@PathVariable Long id,@RequestParam String reason,Principal actor,RedirectAttributes flash){try{service.correctBoarding(id,reason,actor.getName());flash.addFlashAttribute("successMessage","Boarding corrected with a retained audit reason. Previous scans are retained.");}catch(IllegalArgumentException|IllegalStateException invalid){flash.addFlashAttribute("errorMessage",invalid.getMessage());}return "redirect:/dashboard/validate";}
 @PostMapping("/dashboard/boarding-logs/{id}/archive")public String archive(@PathVariable Long id,Principal actor){service.archiveLog(id,true,actor.getName());return "redirect:/dashboard/boarding-logs";}
 @PostMapping("/dashboard/boarding-logs/{id}/restore")public String restore(@PathVariable Long id,Principal actor){service.archiveLog(id,false,actor.getName());return "redirect:/dashboard/boarding-logs?archived=true";}
}
