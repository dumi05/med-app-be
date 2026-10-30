package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

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

    @Column(name = "attachment_stored_name")
    private String attachmentStoredName;

    @Column(name = "attachment_content_type")
    private String attachmentContentType;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    public boolean hasAttachment() {
        return StringUtils.isNoneEmpty(attachmentStoredName);
    }
}
