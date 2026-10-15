package com.dc.medapp.medappbe.reports.dto;

import com.dc.medapp.medappbe.reports.MedicalReportEntity;

import java.time.Instant;

public record MedicalReportResponse(
        Long id,
        Long appointmentId,
        String diagnosisText,
        String attachmentUrl,
        Instant completedAt
) {
    public static MedicalReportResponse fromEntity(MedicalReportEntity entity) {
        return new MedicalReportResponse(
                entity.getId(),
                entity.getAppointment().getId(),
                entity.getDiagnosisText(),
                entity.getAttachmentUrl(),
                entity.getCompletedAt()
        );
    }
}
