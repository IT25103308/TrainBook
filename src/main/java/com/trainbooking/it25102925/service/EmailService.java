package com.trainbooking.it25102925.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for sending automated transactional emails and PDF e-ticket attachments.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @org.springframework.beans.factory.annotation.Value("${app.email.enabled:false}")
    private boolean enabled;

    public enum DeliveryResult { DISABLED, SMTP_ACCEPTED, FAILED }
    public boolean isEnabled() { return enabled; }

    /**
     * Sends a plain-text email message.
     *
     * @param to recipient email address
     * @param subject email subject
     * @param text body text
     */
    public DeliveryResult sendSimpleEmail(String to, String subject, String text) {
        if (!enabled) { log.info("Email delivery disabled; message was not sent."); return DeliveryResult.DISABLED; }
        log.info("Sending simple email to: {}, Subject: {}", to, subject);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@trainbooking.lk");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            return DeliveryResult.SMTP_ACCEPTED;
        } catch (Exception ex) {
            log.warn("SMTP email could not be sent: {}", ex.getMessage());
            return DeliveryResult.FAILED;
        }
    }

    /**
     * Sends an email message containing a binary file attachment (e.g. PDF ticket).
     *
     * @param to recipient email address
     * @param subject email subject
     * @param text body text
     * @param attachment raw byte array of the attachment
     * @param filename name of attached file
     */
    public DeliveryResult sendEmailWithAttachment(String to, String subject, String text, byte[] attachment, String filename) {
        if (!enabled) { log.info("Email delivery disabled; attachment was not sent."); return DeliveryResult.DISABLED; }
        log.info("Sending email with attachment '{}' to: {}, Subject: {}", filename, to, subject);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom("noreply@trainbooking.lk");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);
            if (attachment != null && attachment.length > 0) {
                helper.addAttachment(filename, new ByteArrayResource(attachment));
            }
            mailSender.send(message);
            return DeliveryResult.SMTP_ACCEPTED;
        } catch (Exception ex) {
            log.warn("SMTP attachment email could not be sent: {}", ex.getMessage());
            return DeliveryResult.FAILED;
        }
    }
}
