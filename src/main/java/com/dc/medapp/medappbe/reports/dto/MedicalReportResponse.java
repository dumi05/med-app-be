package com.dc.medapp.medappbe.reports.dto;

import com.dc.medapp.medappbe.reports.MedicalReportEntity;

import java.time.Instant;

public record MedicalReportResponse(
        Long id,
        Long appointmentId,
        String diagnosisText,
        AttachmentInfo attachment,
        Instant completedAt
) {
    public static MedicalReportResponse fromEntity(MedicalReportEntity entity) {
        return new MedicalReportResponse(
                entity.getId(),
                entity.getAppointment().getId(),
                entity.getDiagnosisText(),
                entity.hasAttachment() ? new AttachmentInfo(entity.getAttachmentContentType(), entity.getAttachmentSize()) : null,
                entity.getCompletedAt()
        );
    }
}
