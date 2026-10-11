package com.trainbooking.it25101520;

import com.trainbooking.it25101520.model.*;
import com.trainbooking.it25101520.repository.*;
import com.trainbooking.it25101520.service.*;
import org.junit.jupiter.api.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordRecoveryTest {
    static final String TOKEN="12345678-1234-1234-1234-123456789abc";
    UserRepository users; PasswordResetTokenRepository tokens; PasswordEncoder encoder; PasswordResetDelivery delivery; AuthService service; User user;
    @BeforeEach void setup() {
        users=mock(UserRepository.class);tokens=mock(PasswordResetTokenRepository.class);encoder=mock(PasswordEncoder.class);delivery=mock(PasswordResetDelivery.class);
        service=new AuthService(users,tokens,encoder,delivery,mock(AccountSessions.class));user=User.builder().id(7L).email("fixture@test.invalid").password("old hash").build();
    }
    PasswordResetToken token(LocalDateTime expiry) { return PasswordResetToken.builder().token(AuthService.tokenHash(TOKEN)).user(user).expiryDate(expiry).build(); }
    @Test void issuedTokenIsHashedAndReplacesEarlierTokens() {
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        var captor=org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        String raw=service.createPasswordResetToken(" FIXTURE@TEST.INVALID ");
        verify(tokens).deleteByUserId(7L);verify(tokens).save(captor.capture());
        assertNotEquals(raw,captor.getValue().getToken());assertEquals(AuthService.tokenHash(raw),captor.getValue().getToken());
        assertTrue(captor.getValue().getExpiryDate().isAfter(LocalDateTime.now().plusMinutes(29)));
    }
    @Test void nonexistentAccountDoesNotCreateOrDeliverToken() {
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.empty());service.sendPasswordResetEmail(user.getEmail());
        verifyNoInteractions(tokens,encoder,delivery);
    }
    @Test void expiredOrMissingTokenCannotChangePassword() {
        when(tokens.findByToken(AuthService.tokenHash(TOKEN))).thenReturn(Optional.of(token(LocalDateTime.now().minusSeconds(1))));
        assertFalse(service.resetPassword(TOKEN,"NewPass123"));assertFalse(service.resetPassword("bad token","NewPass123"));
        verify(users,never()).save(any());verifyNoInteractions(encoder);
    }
    @Test void tokenConsumedWhileWaitingForUserLockCannotBeUsedAgain() {
        when(tokens.findByToken(AuthService.tokenHash(TOKEN))).thenReturn(Optional.of(token(LocalDateTime.now().plusMinutes(10))));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));when(tokens.findByTokenForUpdate(AuthService.tokenHash(TOKEN))).thenReturn(Optional.empty());
        assertFalse(service.resetPassword(TOKEN,"NewPass123"));verify(users,never()).save(any());verifyNoInteractions(encoder);
    }
    @Test void resetUsesSamePasswordRulesAsRegistration() {
        for(String invalid:List.of("short","        ","x".repeat(73))) assertThrows(IllegalArgumentException.class,()->service.resetPassword(TOKEN,invalid));
        verifyNoInteractions(tokens,encoder,users);
    }
    @Test void nullExpiryFailsClosed() { assertTrue(token(null).isExpired()); }
    @Test void consoleModeDoesNotContactMailServer() {
        var mail=mock(JavaMailSender.class);var transport=new PasswordResetDelivery(mail);
        ReflectionTestUtils.setField(transport,"deliveryMode","console");ReflectionTestUtils.setField(transport,"publicBaseUrl","http://localhost:8080");
        transport.deliver(user.getEmail(),TOKEN);verifyNoInteractions(mail);
    }
    @Test void smtpUsesConfiguredBaseUrlAndActualMailSender() {
        var mail=mock(JavaMailSender.class);var transport=new PasswordResetDelivery(mail);
        ReflectionTestUtils.setField(transport,"deliveryMode","smtp");ReflectionTestUtils.setField(transport,"publicBaseUrl","https://train.example");
        ReflectionTestUtils.setField(transport,"from","demo@train.example");transport.deliver(user.getEmail(),TOKEN);
        var captured=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail).send(captured.capture());
        assertTrue(captured.getValue().getText().contains("https://train.example/reset-password?token="+TOKEN));
        assertEquals(user.getEmail(),captured.getValue().getTo()[0]);
    }
    @Test void invalidDeliveryConfigurationDoesNotExposeOrSendLink() {
        var mail=mock(JavaMailSender.class);var transport=new PasswordResetDelivery(mail);
        ReflectionTestUtils.setField(transport,"deliveryMode","smtp");ReflectionTestUtils.setField(transport,"publicBaseUrl","https://trusted.example@attacker.example");
        assertThrows(IllegalStateException.class,()->transport.deliver(user.getEmail(),TOKEN));verifyNoInteractions(mail);
    }
}
