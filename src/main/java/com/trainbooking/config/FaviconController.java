package com.trainbooking.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/** Supports browsers that request the conventional icon URL before reading the page head. */
@Controller
public class FaviconController {
    @GetMapping("/favicon.ico")
    public RedirectView favicon() {
        return new RedirectView("/images/favicon.svg", true);
    }
}
