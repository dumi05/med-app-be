package com.dc.medapp.medappbe.delegations.dto;

import com.dc.medapp.medappbe.delegations.ReportDelegationEntity;

import java.time.Instant;
import java.time.LocalDate;

public record ReportDelegationResponse(
        Long id,
        String delegateName,
        String delegateEmail,
        String token,
        LocalDate expiresAt,
        boolean revoked,
        boolean active,
        boolean includeAttachments,
        Instant createdAt
) {
    public static ReportDelegationResponse fromEntity(ReportDelegationEntity entity) {
        return new ReportDelegationResponse(
                entity.getId(),
                entity.getDelegateName(),
                entity.getDelegateEmail(),
                entity.getToken(),
                entity.getExpiresAt(),
                entity.isRevoked(),
                entity.isActive(LocalDate.now()),
                entity.isIncludeAttachments(),
                entity.getCreatedAt()
        );
    }
}
