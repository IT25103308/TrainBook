package com.trainbooking.it25102925.events;
import com.trainbooking.it25102925.model.ServiceChangeAudit;
import com.trainbooking.it25102925.repository.ServiceChangeAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.*;

@Component @Order(0) @RequiredArgsConstructor
public class ServiceAuditObserver implements ServiceChangeObserver {
    private final ServiceChangeAuditRepository repository;
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void onServiceChange(ServiceChange event) {
        if(repository.existsByEventId(event.eventId())) return;
        repository.save(ServiceChangeAudit.builder().eventId(event.eventId()).scheduleId(event.scheduleId()).serviceDate(event.serviceDate())
            .previousStatus(event.previousStatus()).newStatus(event.status()).delayMinutes(event.delayMinutes()).reason(event.reason()).actor(event.actor()).changedAt(event.changedAt()).build());
    }
}
