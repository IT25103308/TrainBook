package com.trainbooking.it25102925.events;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import java.util.List;

/** Observer subject: writers announce changes without knowing notification/audit implementations. */
@Component @Slf4j
public class ServiceChangeSubject {
    private final List<ServiceChangeObserver> observers;
    public ServiceChangeSubject(List<ServiceChangeObserver> observers) { this.observers=List.copyOf(observers); }
    public void publishAfterCommit(ServiceChange change) {
        if(!TransactionSynchronizationManager.isSynchronizationActive()) throw new IllegalStateException("A service change must be published inside its transaction.");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                for(ServiceChangeObserver observer:observers) {
                    try { observer.onServiceChange(change); }
                    catch(Exception failure) { log.error("Committed service change {}: observer {} failed",change.eventId(),observer.getClass().getSimpleName(),failure); }
                }
            }
        });
    }
}
