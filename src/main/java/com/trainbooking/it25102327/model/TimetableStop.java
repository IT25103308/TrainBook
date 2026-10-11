package com.trainbooking.it25102327.model;
import jakarta.persistence.*;
import lombok.*;

/** Intermediate stop times are offsets from departure, so dated timetable changes shift them consistently. */
@Entity @Table(name="timetable_stops") @Getter @Setter @NoArgsConstructor
public class TimetableStop {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="schedule_id",nullable=false) private Schedule schedule;
    private int position;
    @Column(nullable=false,length=100) private String station;
    private int arrivalOffset;
    private int departureOffset;
    @Column(nullable=false,length=30) private String platform;
}
