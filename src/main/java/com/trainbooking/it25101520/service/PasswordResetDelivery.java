package com.trainbooking.it25101520.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Console delivery is an explicit local demonstration mode, not proof of email delivery. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetDelivery {
    private final JavaMailSender mailSender;
    @Value("${trainbook.password-reset.delivery:console}") private String deliveryMode;
    @Value("${trainbook.public-base-url:http://localhost:8080}") private String publicBaseUrl;
    @Value("${trainbook.mail.from:noreply@trainbooking.lk}") private String from;

    public boolean isConsoleMode() { return "console".equalsIgnoreCase(deliveryMode); }

    public void deliver(String email, String token) {
        java.net.URI base = java.net.URI.create(publicBaseUrl);
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalStateException("Configure a valid trainbook.public-base-url.");
        }
        String link = publicBaseUrl.replaceAll("/+$", "") + "/reset-password?token=" + token;
        if (isConsoleMode()) {
            // Deliberately limited to the local application's trusted console. Never expose this in a browser response.
            log.info("LOCAL DEMO password reset for {} (expires in 30 minutes): {}", email, link);
            return;
        }
        if (!"smtp".equalsIgnoreCase(deliveryMode)) throw new IllegalStateException("Choose console or smtp password-reset delivery.");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Reset your TrainBook password");
        message.setText("Use this link within 30 minutes to set a new password:\n" + link
                + "\n\nThe link can be used once. If you did not request it, ignore this message.");
        mailSender.send(message);
    }
}
