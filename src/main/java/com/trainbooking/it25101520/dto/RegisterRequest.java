package com.trainbooking.it25101520.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for new user registration requests.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must be at most 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must be at most 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 254, message = "Email must be at most 254 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must contain 8–72 characters; non-ASCII characters may reach the byte limit sooner")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    @jakarta.validation.constraints.AssertTrue(message = "Please agree to the Terms of Service and Privacy Policy")
    private boolean acceptedTerms;

    private String phone;
    private String phoneNumber;

    @NotBlank(message = "Phone number is required")
    @Size(max = 20, message = "Phone number must be at most 20 characters")
    public String getPhone() {
        return (phone != null && !phone.isBlank()) ? phone : phoneNumber;
    }
}
