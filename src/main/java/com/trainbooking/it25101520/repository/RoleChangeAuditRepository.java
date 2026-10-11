package com.trainbooking.it25101520.repository;
import com.trainbooking.it25101520.model.RoleChangeAudit;import org.springframework.data.jpa.repository.JpaRepository;import java.util.List;
public interface RoleChangeAuditRepository extends JpaRepository<RoleChangeAudit,Long>{List<RoleChangeAudit> findTop30ByOrderByIdDesc();}
