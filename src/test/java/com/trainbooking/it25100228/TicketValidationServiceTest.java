package com.trainbooking.it25100228;
import com.trainbooking.it25100228.service.TicketValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class TicketValidationServiceTest {
 @ParameterizedTest @ValueSource(strings={"123","{\"ticketNumber\":\"TKT-2026-10001\"}","TKT-123<script>","", "TKT-", "https://example.com/TKT-2026-1"})void malformedIdentifiersNeverBecomeAnotherTicket(String text){assertNull(TicketValidationService.normalize(text));}
 @Test void exactIssuedReferenceAcceptsCaseAndSurroundingWhitespace(){assertEquals("TKT-2026-10001",TicketValidationService.normalize("  tkt-2026-10001  "));assertEquals("TKT-2026-10001",TicketValidationService.normalize("\"TKT-2026-10001\""));assertNull(TicketValidationService.normalize(null));assertNull(TicketValidationService.normalize("TKT-"+"1".repeat(300)));}
}
