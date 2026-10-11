package com.trainbooking.it25102327.repository;
import com.trainbooking.it25102327.model.PlanningLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface PlanningLockRepository extends JpaRepository<PlanningLock,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from PlanningLock p where p.id=1")
    Optional<PlanningLock> lockPlan();
}
