package com.trainbooking.it25101520.service;

import com.trainbooking.it25101520.dto.RegisterRequest;
import com.trainbooking.it25101520.model.PasswordResetToken;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.PasswordResetTokenRepository;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.validation.ContactRules;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/** Authentication and single-use recovery. Roles cannot be supplied by public registration. */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetDelivery passwordResetDelivery;
    private final AccountSessions accountSessions;

    @Transactional
    public User registerUser(RegisterRequest request) {
        if (request == null) throw new IllegalArgumentException("Please enter your account details.");
        String firstName = ContactRules.name(request.getFirstName(), "First name");
        String lastName = ContactRules.name(request.getLastName(), "Last name");
        String email = ContactRules.email(request.getEmail());
        String phone = ContactRules.phone(request.getPhone());
        ContactRules.password(request.getPassword());
        if (!request.getPassword().equals(request.getConfirmPassword())) throw new IllegalArgumentException("Passwords do not match.");
        if (!request.isAcceptedTerms()) throw new IllegalArgumentException("Please agree to the Terms of Service and Privacy Policy.");
        if (userRepository.existsByEmail(email)) throw new IllegalArgumentException("An account with this email already exists. Please sign in.");
        return userRepository.save(User.builder().firstName(firstName).lastName(lastName).email(email)
                .phone(phone).password(passwordEncoder.encode(request.getPassword())).role(User.Role.PASSENGER).build());
    }

    @Transactional
    public String createPasswordResetToken(String email) {
        User found = userRepository.findByEmail(ContactRules.email(email)).orElse(null);
        if (found == null) return null;
        User user = userRepository.findByIdForUpdate(found.getId()).orElseThrow();
        passwordResetTokenRepository.deleteByUserId(user.getId());
        passwordResetTokenRepository.flush();
        String rawToken = UUID.randomUUID().toString();
        passwordResetTokenRepository.save(PasswordResetToken.builder().token(tokenHash(rawToken))
                .user(user).expiryDate(LocalDateTime.now().plusMinutes(30)).build());
        return rawToken;
    }

    @Transactional
    public void sendPasswordResetEmail(String email) {
        String rawToken = createPasswordResetToken(email);
        if (rawToken != null) passwordResetDelivery.deliver(ContactRules.email(email), rawToken);
    }

    public boolean isConsoleResetDelivery() { return passwordResetDelivery.isConsoleMode(); }

    @Transactional(readOnly = true)
    public boolean validatePasswordResetToken(String token) {
        if (!isToken(token)) return false;
        return passwordResetTokenRepository.findByToken(tokenHash(token)).filter(t -> !t.isExpired()).isPresent();
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        ContactRules.password(newPassword);
        if (!isToken(token)) return false;
        String hashedToken = tokenHash(token);
        PasswordResetToken found = passwordResetTokenRepository.findByToken(hashedToken).orElse(null);
        if (found == null || found.isExpired() || found.getUser() == null) return false;
        // All recovery operations lock user before token, including a concurrent reissue.
        User user = userRepository.findByIdForUpdate(found.getUser().getId()).orElseThrow();
        PasswordResetToken active = passwordResetTokenRepository.findByTokenForUpdate(hashedToken).orElse(null);
        if (active == null || active.isExpired() || !user.getId().equals(active.getUser().getId())) return false;
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        passwordResetTokenRepository.deleteByUserId(user.getId());
        passwordResetTokenRepository.flush();
        accountSessions.invalidateAfterCommit(user.getEmail());
        return true;
    }

    private static boolean isToken(String value) {
        return value != null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    public static String tokenHash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
}
