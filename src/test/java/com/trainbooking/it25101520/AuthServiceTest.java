package com.trainbooking.it25101520;

import com.trainbooking.it25101520.dto.RegisterRequest;
import com.trainbooking.it25101520.model.User;
import com.trainbooking.it25101520.repository.UserRepository;
import com.trainbooking.it25101520.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.trainbooking.it25101520.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock private com.trainbooking.it25101520.service.PasswordResetDelivery passwordResetDelivery;

    @Mock private com.trainbooking.it25101520.service.AccountSessions accountSessions;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleRequest = RegisterRequest.builder()
                .firstName("Sunil")
                .lastName("Silva")
                .email("sunil@example.com")
                .password("SecurePass123")
                .confirmPassword("SecurePass123")
                .phone("+94771234567").acceptedTerms(true)
                .build();
    }

    @Test
    @DisplayName("Should successfully register a new passenger user with encoded password")
    void testRegisterUser_Success() {
        when(userRepository.existsByEmail("sunil@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123")).thenReturn("hashedPasswordXYZ");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        User result = authService.registerUser(sampleRequest);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("sunil@example.com", result.getEmail());
        assertEquals("hashedPasswordXYZ", result.getPassword());
        assertEquals(User.Role.PASSENGER, result.getRole());
        assertEquals("Sunil", result.getFirstName());
        assertEquals("Silva", result.getLastName());
        assertEquals("0771234567", result.getPhone());

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when email is already registered")
    void testRegisterUser_DuplicateEmail() {
        when(userRepository.existsByEmail("sunil@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.registerUser(sampleRequest)
        );

        assertTrue(ex.getMessage().contains("already exists"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should process password reset for existing user")
    void testSendPasswordResetEmail_ExistingUser() {
        User user = User.builder()
                .id(1L)
                .email("sunil@example.com")
                .build();
        when(userRepository.findByEmail("sunil@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> authService.sendPasswordResetEmail("sunil@example.com"));
        verify(userRepository, times(1)).findByEmail("sunil@example.com");
    }

    @Test
    @DisplayName("Should create and validate password reset token successfully")
    void testPasswordResetToken_Lifecycle() {
        User user = User.builder().id(1L).email("sunil@example.com").build();
        when(userRepository.findByEmail("sunil@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        String token = authService.createPasswordResetToken("sunil@example.com");
        assertNotNull(token);
        verify(passwordResetTokenRepository, times(1)).save(any(com.trainbooking.it25101520.model.PasswordResetToken.class));

        com.trainbooking.it25101520.model.PasswordResetToken prt = com.trainbooking.it25101520.model.PasswordResetToken.builder()
                .token(AuthService.tokenHash(token))
                .user(user)
                .expiryDate(java.time.LocalDateTime.now().plusMinutes(30))
                .build();
        when(passwordResetTokenRepository.findByToken(AuthService.tokenHash(token))).thenReturn(Optional.of(prt));

        boolean isValid = authService.validatePasswordResetToken(token);
        assertTrue(isValid);

        when(passwordResetTokenRepository.findByTokenForUpdate(AuthService.tokenHash(token))).thenReturn(Optional.of(prt));
        when(passwordEncoder.encode("NewPass123")).thenReturn("encodedNewPass");
        boolean resetSuccess = authService.resetPassword(token, "NewPass123");
        assertTrue(resetSuccess);
        verify(userRepository, times(1)).save(user);
        verify(passwordResetTokenRepository, times(2)).deleteByUserId(1L);
    }
}
