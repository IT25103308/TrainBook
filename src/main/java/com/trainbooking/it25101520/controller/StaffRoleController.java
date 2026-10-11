package com.trainbooking.it25101520.controller;
import com.trainbooking.it25101520.service.StaffRoleService;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Controller;import org.springframework.web.bind.annotation.*;import org.springframework.ui.Model;import org.springframework.web.servlet.mvc.support.RedirectAttributes;import java.security.Principal;
@Controller @RequiredArgsConstructor @RequestMapping("/admin/accounts")public class StaffRoleController {
 private final StaffRoleService service;
 @GetMapping public String list(Model model){model.addAttribute("pageTitle","Staff access");model.addAttribute("accounts",service.accounts());model.addAttribute("changes",service.history());return "it25101520/staff-access";}
 @PostMapping("/{id}/role")public String change(@PathVariable Long id,@RequestParam String role,@RequestParam String reason,Principal actor,RedirectAttributes flash){try{service.change(id,role,reason,actor.getName());flash.addFlashAttribute("successMessage","Role saved with an audit reason. The account must sign in again to use its updated access.");}catch(IllegalArgumentException|IllegalStateException invalid){flash.addFlashAttribute("errorMessage",invalid.getMessage());}return "redirect:/admin/accounts";}
}
