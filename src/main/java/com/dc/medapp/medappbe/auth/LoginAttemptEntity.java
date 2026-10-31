package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Table(name = "login_attempt")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginAttemptEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private int failures;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "last_failure_at")
    private Instant lastFailureAt;
}
