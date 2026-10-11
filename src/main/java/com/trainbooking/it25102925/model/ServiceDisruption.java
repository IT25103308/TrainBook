package com.trainbooking.it25102925.model;

import com.trainbooking.it25102327.model.Schedule;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

/** An operational status belongs to one service departure date, not every trip of a train. */
@Entity @Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name="service_disruptions",uniqueConstraints=@UniqueConstraint(columnNames={"schedule_id","service_date"}))
public class ServiceDisruption {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="schedule_id",nullable=false)
    @ToString.Exclude @EqualsAndHashCode.Exclude private Schedule schedule;
    @Column(name="service_date",nullable=false) private LocalDate serviceDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,columnDefinition="varchar(20)") private Status status;
    private Integer delayMinutes;
    @Column(length=300) private String reason;
    @Column(length=254) private String changedBy;
    private LocalDateTime changedAt;
    public enum Status { ON_TIME, DELAYED, CANCELLED }
}
