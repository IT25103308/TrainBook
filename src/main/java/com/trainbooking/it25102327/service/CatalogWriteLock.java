package com.trainbooking.it25102327.service;
import com.trainbooking.it25102327.model.PlanningLock;
import com.trainbooking.it25102327.repository.PlanningLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class CatalogWriteLock implements ApplicationRunner {
    private final PlanningLockRepository repository;
    @Override public void run(ApplicationArguments args) {
        if (!repository.existsById(1L)) {
            try { repository.saveAndFlush(new PlanningLock(1L)); }
            catch (org.springframework.dao.DataIntegrityViolationException concurrentStartup) {
                if (!repository.existsById(1L)) throw concurrentStartup;
            }
        }
    }
    public void acquire() {
        repository.lockPlan().orElseThrow(()->new IllegalStateException("The planning lock is unavailable. Restart the application before editing the network."));
    }
}
