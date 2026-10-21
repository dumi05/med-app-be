package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "medical_report")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MedicalReportEntity extends AuditableEntity {
    @OneToOne(optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private AppointmentEntity appointment;

    @Column(name = "diagnosis_text", nullable = false, length = 5000)
    private String diagnosisText;

    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;
}
