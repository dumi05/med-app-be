package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.common.BaseEntity;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "availability_slot")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AvailabilitySlotEntity extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "doc_id")
    private DoctorEntity doctor;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "booked", nullable = false)
    private boolean booked;

    @Builder.Default
    @Column(name = "generated", nullable = false, columnDefinition = "boolean default false")
    private boolean generated = false;

    public LocalDateTime endTime() {
        return startTime.plusMinutes(durationMinutes);
    }

    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd){
        return startTime.isBefore(otherEnd) && endTime().isAfter(otherStart);
    }
}
