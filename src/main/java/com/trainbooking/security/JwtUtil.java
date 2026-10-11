package com.trainbooking.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility component for generating, parsing, and validating JSON Web Tokens (JWT).
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Component
@Slf4j
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Generates a signed JWT token for the specified user email subject.
     *
     * @param email the user's email address
     * @return the generated JWT token string
     */
    public String generateToken(String email) {
        log.debug("Generating JWT token for email: {}", email);
        // TODO: Build and sign JWT token using jjwt library with secretKey and expirationMs
        return "mock-jwt-token-for-" + email;
    }

    /**
     * Extracts the subject (email) from the provided JWT token.
     *
     * @param token the JWT token string
     * @return the extracted email address
     */
    public String extractEmail(String token) {
        log.debug("Extracting email from token: {}", token);
        // TODO: Parse JWT claims using secretKey and retrieve subject (email)
        return null;
    }

    /**
     * Validates whether a token is valid and corresponds to the given user email.
     *
     * @param token the JWT token string
     * @param email the user's email address to match
     * @return true if the token is valid and not expired, false otherwise
     */
    public boolean isTokenValid(String token, String email) {
        log.debug("Validating token for email: {}", email);
        // TODO: Validate token signature, expiration date, and match subject against email
        return false;
    }
}
