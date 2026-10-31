package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@Entity
@Table(name = "app_user")
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String passwordHash;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean enabled = true;

    @Builder.Default
    @Column(name = "token_version", nullable = false, columnDefinition = "integer default 0")
    private int tokenVersion = 0;

    @Builder.Default
    @Column(name = "must_change_password", nullable = false, columnDefinition = "boolean default false")
    private boolean mustChangePassword = false;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
