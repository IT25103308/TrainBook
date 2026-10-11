package com.trainbooking.it25101520.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Invalidates the changed account's sessions only after its new password commits. */
@Service
@RequiredArgsConstructor
public class AccountSessions {
    private final SessionRegistry sessions;

    public void invalidateAfterCommit(String email) {
        Runnable revoke = () -> sessions.getAllPrincipals().stream()
                .filter(p -> p instanceof UserDetails u && u.getUsername().equalsIgnoreCase(email))
                .forEach(p -> sessions.getAllSessions(p,false).forEach(s -> s.expireNow()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { revoke.run(); }
            });
        } else { revoke.run(); }
    }
}
