package com.trainbooking.it25101520.service;

import com.trainbooking.it25101520.dto.RegisterRequest;
import com.trainbooking.it25101520.dto.UserProfileDto;
import com.trainbooking.validation.ContactRules;
import org.springframework.validation.Errors;

/** Adds field-specific errors and is also reused by services to enforce direct-call validation. */
public final class AccountFormRules {
    private AccountFormRules() { }

    public static void registration(RegisterRequest form, Errors errors) {
        check(errors, "firstName", () -> ContactRules.name(form.getFirstName(), "First name"));
        check(errors, "lastName", () -> ContactRules.name(form.getLastName(), "Last name"));
        check(errors, "phone", () -> ContactRules.phone(form.getPhone()));
        check(errors, "email", () -> ContactRules.email(form.getEmail()));
        check(errors, "password", () -> ContactRules.password(form.getPassword()));
    }

    public static void profile(UserProfileDto form, Errors errors) {
        check(errors, "firstName", () -> ContactRules.name(form.getFirstName(), "First name"));
        check(errors, "lastName", () -> ContactRules.name(form.getLastName(), "Last name"));
        check(errors, "phone", () -> ContactRules.phone(form.getPhone()));
        check(errors, "delayAlertThresholdMinutes", () -> threshold(form.getDelayAlertThresholdMinutes()));
    }

    public static void threshold(Integer value) {
        if (value == null || !java.util.Set.of(5, 15, 30, 60).contains(value)) {
            throw new IllegalArgumentException("Choose a delay threshold of 5, 15, 30 or 60 minutes.");
        }
    }

    private static void check(Errors errors, String field, Runnable validation) {
        if (errors.hasFieldErrors(field)) return;
        try { validation.run(); }
        catch (IllegalArgumentException ex) { errors.rejectValue(field, "invalid." + field, ex.getMessage()); }
    }
}
