package com.dc.medapp.medappbe.audit;

import com.dc.medapp.medappbe.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "audit_log")
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuditLogEntity extends BaseEntity {
    @Column(name = "user_email", nullable = false)
    private String userEmail;
    @Column(name = "action", nullable = false)
    private  String action;
    @Column(name = "entity_name", nullable = false)
    private String entityName;
    @Column(name = "entity_id")
    private Long entityId;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private AuditOutcome outcome;
    @Column(name = "failure_reason")
    private String failureReason;
}
