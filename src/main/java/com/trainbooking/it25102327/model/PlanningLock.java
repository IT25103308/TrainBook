package com.trainbooking.it25102327.model;
import jakarta.persistence.*;
import lombok.*;
/** One durable row serializes administrative network edits across application instances. */
@Entity @Table(name="planning_lock") @Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PlanningLock { @Id private Long id; }
