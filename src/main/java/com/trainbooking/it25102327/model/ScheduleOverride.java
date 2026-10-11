package com.trainbooking.it25102327.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.math.BigDecimal;

@Entity @Table(name="schedule_overrides",uniqueConstraints=@UniqueConstraint(columnNames={"schedule_id","service_date","kind"}))
@Getter @Setter @NoArgsConstructor
public class ScheduleOverride {
    public enum Kind { TIMETABLE, MAINTENANCE }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="schedule_id",nullable=false) private Schedule schedule;
    @Column(name="service_date",nullable=false) private LocalDate serviceDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Kind kind;
    @Column(nullable=false,length=300) private String label;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private Integer arrivalDayOffset;
    @Column(precision=10,scale=2) private BigDecimal firstClassFare;
    @Column(precision=10,scale=2) private BigDecimal secondClassFare;
}
