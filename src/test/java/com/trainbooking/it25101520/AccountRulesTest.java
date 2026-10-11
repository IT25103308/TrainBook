package com.trainbooking.it25101520;

import com.trainbooking.validation.ContactRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import static org.junit.jupiter.api.Assertions.*;

class AccountRulesTest {
    @ParameterizedTest @ValueSource(strings={"Anfas", "O'Neil", "Jean-Luc", "A. Silva", "සිල්වා", "José"})
    void realNameFormatsAreAccepted(String value) { assertEquals(value,ContactRules.name(value,"Name")); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","1234","<script>","---","Name\nOther"})
    void invalidNamesAreRejected(String value) { assertThrows(IllegalArgumentException.class,()->ContactRules.name(value,"Name")); }
    @Test void nameLengthAndWhitespaceAreHandled() {
        assertEquals("A B",ContactRules.name("  A   B  ","Name"));
        assertEquals(100,ContactRules.name("A".repeat(100),"Name").length());
        assertThrows(IllegalArgumentException.class,()->ContactRules.name("A".repeat(101),"Name"));
    }
    @ParameterizedTest @ValueSource(strings={"0771234567","+94771234567","0094771234567","077 123 4567","077-123-4567"})
    void phonesNormalizeConsistently(String value) { assertEquals("0771234567",ContactRules.phone(value)); }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"077123456","07712345678","0771234567abc","+44771234567","0121234567","+94+771234567","077/1234567"})
    void invalidPhonesAreRejected(String value) { assertThrows(IllegalArgumentException.class,()->ContactRules.phone(value)); }
    @Test void fixedLineNumbersRemainSupported() { assertEquals("0112345678",ContactRules.phone("+94112345678")); }
    @Test void emailNormalizesButDoesNotAllowWhitespaceOrHeaders() {
        assertEquals("a@example.com",ContactRules.email(" A@EXAMPLE.COM "));
        assertThrows(IllegalArgumentException.class,()->ContactRules.email("a@example.com\r\nBcc: victim"));
    }
    @Test void passwordBoundariesPreserveExactValueAndPreventBcryptTruncation() {
        assertDoesNotThrow(()->ContactRules.password(" Pass123 "));
        assertDoesNotThrow(()->ContactRules.password("A".repeat(72)));
        assertThrows(IllegalArgumentException.class,()->ContactRules.password("A".repeat(73)));
        assertThrows(IllegalArgumentException.class,()->ContactRules.password("සි".repeat(20)));
        assertThrows(IllegalArgumentException.class,()->ContactRules.password("short"));
        assertThrows(IllegalArgumentException.class,()->ContactRules.password("        "));
    }
    @Test void emailValidationUsesTheSameLibraryRulesAsRegistration() {
        for(String value:new String[]{"<script>@example.com","a@example,com.test","a..b@example.com","a@-example.com"}) {
            assertThrows(IllegalArgumentException.class,()->ContactRules.email(value));
        }
        assertEquals("a+tag@example.com",ContactRules.email("A+tag@example.com"));
    }
}
