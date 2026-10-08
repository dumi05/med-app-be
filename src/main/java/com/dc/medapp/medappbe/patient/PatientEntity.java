package com.dc.medapp.medappbe.patient;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "patient")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientEntity extends BaseEntity {
    @OneToOne(optional = false)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    private String firstName;
    private String lastName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "gdpr_consent", nullable = false)
    private boolean gdprConsent;

    @Column(name = "gdpr_consent_timestamp", nullable = false)
    private Instant gdprConsentTimestamp;

    // serve per indicare quale versione del consenso è stata accettata, utile per eventuali aggiornamenti futuri
    @Column(name = "gdpr_consent_version", nullable = false)
    private String gdprConsentVersion;

    @Column(name = "anonymized", nullable = false)
    private boolean anonymized;

}
