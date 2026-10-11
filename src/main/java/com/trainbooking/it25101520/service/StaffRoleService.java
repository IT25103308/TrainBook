package com.trainbooking.it25101520.service;
import com.trainbooking.it25101520.model.*;import com.trainbooking.it25101520.repository.*;import com.trainbooking.exception.ResourceNotFoundException;import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;import jakarta.persistence.EntityManager;import java.time.LocalDateTime;import java.util.*;
@Service @RequiredArgsConstructor
public class StaffRoleService {
 private final UserRepository users;private final RoleChangeAuditRepository audit;private final AccountSessions sessions;private final EntityManager em;
 @Transactional(readOnly=true)public List<User> accounts(){return users.findAll().stream().sorted(Comparator.comparing(User::getEmail)).toList();}
 @Transactional(readOnly=true)public List<RoleChangeAudit> history(){return audit.findTop30ByOrderByIdDesc();}
 @Transactional(isolation=Isolation.READ_COMMITTED)public void change(Long id,String role,String reason,String actor){
  User operator=users.findByEmail(actor).orElseThrow(()->new ResourceNotFoundException("Operator not found."));if(operator.getRole()!=User.Role.ADMIN)throw new org.springframework.security.access.AccessDeniedException("Administrator access required.");
  User.Role chosen;try{chosen=User.Role.valueOf(role==null?"":role);}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Choose Passenger, Coordinator or Station staff.");}if(chosen==User.Role.ADMIN)throw new IllegalArgumentException("Administrator accounts are managed through controlled setup.");
  reason=com.trainbooking.it25102925.service.ServiceStatusService.plainReason(reason);User target=users.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Account not found."));if(em!=null)em.refresh(target);
  if(target.getRole()==User.Role.ADMIN || Objects.equals(operator.getId(),target.getId()))throw new IllegalStateException("Administrator and current-operator accounts cannot be changed here.");if(target.getRole()==chosen)return;
  audit.save(RoleChangeAudit.builder().accountId(id).accountEmail(target.getEmail()).operatorEmail(actor).previousRole(target.getRole().name()).newRole(chosen.name()).reason(reason).changedAt(LocalDateTime.now()).build());target.setRole(chosen);users.save(target);sessions.invalidateAfterCommit(target.getEmail());
 }
}
