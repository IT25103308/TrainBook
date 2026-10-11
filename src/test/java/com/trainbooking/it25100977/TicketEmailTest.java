package com.trainbooking.it25100977;

import com.trainbooking.it25102925.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketEmailTest {
    @Test void localDemoDoesNotPretendToSendEmailOrContactSmtp() {
        var sender=mock(JavaMailSender.class);var service=new EmailService(sender);
        assertEquals(EmailService.DeliveryResult.DISABLED,service.sendSimpleEmail("test@example.invalid","Test","Body"));
        assertEquals(EmailService.DeliveryResult.DISABLED,service.sendEmailWithAttachment("test@example.invalid","Test","Body",new byte[]{1},"ticket.pdf"));
        verifyNoInteractions(sender);
    }
    @Test void enabledMailReportsServerAcceptanceOrFailureHonestly() {
        var sender=mock(JavaMailSender.class);var service=new EmailService(sender);ReflectionTestUtils.setField(service,"enabled",true);
        assertEquals(EmailService.DeliveryResult.SMTP_ACCEPTED,service.sendSimpleEmail("test@example.invalid","Test","Body"));
        doThrow(new MailSendException("Test SMTP failure")).when(sender).send(any(org.springframework.mail.SimpleMailMessage.class));
        assertEquals(EmailService.DeliveryResult.FAILED,service.sendSimpleEmail("test@example.invalid","Test","Body"));
    }
}
