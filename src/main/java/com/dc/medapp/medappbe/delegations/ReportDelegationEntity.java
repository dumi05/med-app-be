package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.common.AuditableEntity;
import com.dc.medapp.medappbe.patient.PatientEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "report_delegation")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReportDelegationEntity extends AuditableEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientEntity patient;

    @Column(name = "delegate_name", nullable = false)
    private String delegateName;

    @Column(name = "delegate_email", nullable = false)
    private String delegateEmail;

    @Column(name = "token", nullable = false, unique = true, updatable = false)
    private String token;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "include_attachments")
    private boolean includeAttachments;

    @Column(name = "access_code_hash")
    private String accessCodeHash;

    @Builder.Default
    @Column(name = "failed_code_attempts", nullable = false, columnDefinition = "integer default 0")
    private int failedCodeAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Transient
    private String issuedAccessCode;

    public boolean hasAccessCode() {
        return accessCodeHash != null && !accessCodeHash.isEmpty();
    }
    public boolean isActive(LocalDate date) {
        return !revoked && (expiresAt == null || !expiresAt.isBefore(date));
    }
}
