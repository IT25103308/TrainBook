package com.trainbooking.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

/** A usable destination for a denied HTML request, including forwarded POST requests. */
@Controller
public class AccessPageController {
    @RequestMapping("/access-denied")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String accessDenied() { return "it25101520/access-denied"; }
}
