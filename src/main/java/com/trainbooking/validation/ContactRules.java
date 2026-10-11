package com.trainbooking.validation;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Shared format rules; they do not verify ownership of a name or telephone number. */
public final class ContactRules {
    private ContactRules() { }

    public static String name(String value, String label) {
        String name = value == null ? "" : value.strip();
        if (name.isEmpty() || name.length() > 100 || !name.matches("[\\p{L}\\p{M} .'’-]+")
                || !name.matches(".*\\p{L}.*")) {
            throw new IllegalArgumentException(label + " must contain 1–100 characters: letters, spaces, initials, apostrophes or hyphens.");
        }
        return name.replaceAll(" +", " ");
    }

    public static String phone(String value) {
        String raw = value == null ? "" : value.strip();
        if (raw.length() > 20 || !raw.matches("[+0-9 -]+")) throw invalidPhone();
        String phone = raw.replace(" ", "").replace("-", "");
        if (phone.startsWith("+94")) phone = "0" + phone.substring(3);
        else if (phone.startsWith("0094")) phone = "0" + phone.substring(4);
        if (!phone.matches("0(?:7[0-9]|11|21|23|24|25|26|27|31|32|33|34|35|36|37|38|41|45|47|51|52|54|55|57|63|65|66|67|81|91)[0-9]{7}")) throw invalidPhone();
        return phone;
    }

    public static String email(String value) {
        String email = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || !EmailChecks.VALIDATOR.validate(new EmailValue(email)).isEmpty()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return email;
    }

    public static void password(String value) {
        if (value == null || value.isBlank() || value.length() < 8
                || value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Use at least 8 characters and no more than 72 UTF-8 bytes for the password.");
        }
    }

    private static IllegalArgumentException invalidPhone() {
        return new IllegalArgumentException("Enter a Sri Lankan phone number with 10 digits, for example 0771234567 or +94771234567. Spaces and hyphens are allowed.");
    }

    private static final class EmailChecks {
        private static final jakarta.validation.Validator VALIDATOR = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static final class EmailValue {
        @jakarta.validation.constraints.Email
        private final String value;
        private EmailValue(String value) { this.value = value; }
    }
}
