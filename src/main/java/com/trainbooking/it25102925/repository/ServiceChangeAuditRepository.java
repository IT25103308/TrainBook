package com.trainbooking.it25102925.repository;
import com.trainbooking.it25102925.model.ServiceChangeAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ServiceChangeAuditRepository extends JpaRepository<ServiceChangeAudit,Long> {
    List<ServiceChangeAudit> findTop30ByOrderByIdDesc();
    boolean existsByEventId(String eventId);
}
