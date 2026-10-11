package com.trainbooking.it25101520;

import com.trainbooking.it25101520.service.AccountSessions;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

class AccountSessionsTest {
    @Test void passwordChangeExpiresOnlyTheAffectedAccountsSessions() {
        var registry=new SessionRegistryImpl();var own=User.withUsername("own@test.invalid").password("unused").roles("PASSENGER").build();
        var other=User.withUsername("other@test.invalid").password("unused").roles("PASSENGER").build();
        registry.registerNewSession("own1",own);registry.registerNewSession("own2",own);registry.registerNewSession("other",other);
        new AccountSessions(registry).invalidateAfterCommit("OWN@TEST.INVALID");
        assertTrue(registry.getSessionInformation("own1").isExpired());assertTrue(registry.getSessionInformation("own2").isExpired());
        assertFalse(registry.getSessionInformation("other").isExpired());
    }
    @Test void rollbackDoesNotExpireExistingSessions() {
        var registry=new SessionRegistryImpl();var own=User.withUsername("own@test.invalid").password("unused").roles("PASSENGER").build();
        registry.registerNewSession("own",own);TransactionSynchronizationManager.initSynchronization();
        try {
            new AccountSessions(registry).invalidateAfterCommit("own@test.invalid");assertFalse(registry.getSessionInformation("own").isExpired());
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            assertFalse(registry.getSessionInformation("own").isExpired());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
