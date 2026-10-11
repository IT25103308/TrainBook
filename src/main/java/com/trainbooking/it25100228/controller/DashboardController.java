package com.trainbooking.it25100228.controller;
import com.trainbooking.it25100228.dto.DashboardStatsDto;
import com.trainbooking.it25100228.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.ui.Model;
import java.time.*;
import java.util.Map;
@Controller @RequiredArgsConstructor
public class DashboardController {
 private final DashboardService service;
 @GetMapping("/dashboard")public String showDashboard(@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to,Model model){LocalDate start=from==null?LocalDate.now().minusDays(6):from,end=to==null?LocalDate.now():to;try{DashboardService.checkRange(start,end);}catch(IllegalArgumentException invalid){model.addAttribute("errorMessage",invalid.getMessage());start=LocalDate.now().minusDays(6);end=LocalDate.now();}model.addAttribute("stats",service.getDashboardStats());model.addAttribute("dailyRevenue",service.getRevenueByDate(start,end).get("dailyRevenue"));model.addAttribute("report",service.getReconciliationReport(start,end));model.addAttribute("fromIso",start.toString());model.addAttribute("toIso",end.toString());model.addAttribute("monthFrom",LocalDate.now().withDayOfMonth(1).toString());model.addAttribute("todayIso",LocalDate.now().toString());model.addAttribute("recentBookings",service.recentBookings());return "it25100228/index";}
 @GetMapping("/dashboard/revenue")@ResponseBody public Map<String,Object> revenue(@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to){try{return service.getRevenueByDate(from==null?LocalDate.now().minusDays(6):from,to==null?LocalDate.now():to);}catch(IllegalArgumentException invalid){throw new com.trainbooking.exception.ApiException(400,invalid.getMessage());}}
 @GetMapping("/api/dashboard/stats/live")@ResponseBody public DashboardStatsDto live(){return service.getDashboardStats();}
 @GetMapping("/dashboard/boarding-logs")public String logs(@RequestParam(defaultValue="false")boolean archived,Model model){model.addAttribute("pageTitle","Boarding audit");model.addAttribute("logs",service.getRecentBoardingLogs(archived));model.addAttribute("archivedView",archived);return "it25100228/boarding-logs";}
 @GetMapping("/api/dashboard/reconciliation")@ResponseBody public Map<String,Object> reconciliation(@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to){try{return service.getReconciliationReport(from==null?LocalDate.now():from,to==null?LocalDate.now():to);}catch(IllegalArgumentException invalid){throw new com.trainbooking.exception.ApiException(400,invalid.getMessage());}}
}
