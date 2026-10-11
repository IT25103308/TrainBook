package com.trainbooking.it25101520.model;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
@Entity @Table(name="role_change_audit") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RoleChangeAudit {@Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;private Long accountId;@Column(length=254)private String accountEmail;@Column(length=254)private String operatorEmail;@Column(length=32)private String previousRole;@Column(length=32)private String newRole;@Column(length=300)private String reason;private LocalDateTime changedAt;}
