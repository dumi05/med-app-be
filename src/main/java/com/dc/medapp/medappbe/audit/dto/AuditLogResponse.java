package com.dc.medapp.medappbe.audit.dto;

import com.dc.medapp.medappbe.audit.AuditLogEntity;
import com.dc.medapp.medappbe.audit.AuditOutcome;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String userEmail,
        String action,
        String entityName,
        Long entityId,
        Instant occurredAt,
        AuditOutcome outcome,
        String failureReason
) {
    public static AuditLogResponse fromEntity(AuditLogEntity entity) {
        return new AuditLogResponse(
                entity.getId(),
                entity.getUserEmail(),
                entity.getAction(),
                entity.getEntityName(),
                entity.getEntityId(),
                entity.getOccurredAt(),
                entity.getOutcome(),
                entity.getFailureReason()
        );
    }
}
