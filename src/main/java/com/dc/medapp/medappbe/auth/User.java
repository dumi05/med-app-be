package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@Entity
@Table(name = "utente")
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String passwordHash;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
