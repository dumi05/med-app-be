package com.dc.medapp.medappbe.appointments;

import com.dc.medapp.medappbe.common.AuditableEntity;
import com.dc.medapp.medappbe.patient.PatientEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "appointment")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AppointmentEntity extends AuditableEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id")
    private PatientEntity patient;

    @ManyToOne(optional = false)
    @JoinColumn(name = "slot_id")
    private AvailabilitySlotEntity slot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppointmentStatus status;

    @Column(name = "no_show_risk", nullable = false)
    private boolean noShowRisk;

    private Instant completedAt;
    private Instant reportedAt;
    private Instant invoicedAt;
    private Instant cancelledAt;
}
