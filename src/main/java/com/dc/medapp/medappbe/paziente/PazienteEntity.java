package com.dc.medapp.medappbe.paziente;

import com.dc.medapp.medappbe.auth.User;
import com.dc.medapp.medappbe.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "paziente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PazienteEntity extends BaseEntity {
    @OneToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private String nome;
    private String cognome;

    @Column(name = "data_nascita")
    private LocalDate dataNascita;

    @Column(name = "consenso_gdpr", nullable = false)
    private boolean consensoGdpr;

    @Column(name = "consenso_gdpr_timestamp", nullable = false)
    private Instant consensoGdprTimestamp;

    // serve per indicare quale versione del consenso è stata accettata, utile per eventuali aggiornamenti futuri
    @Column(name = "consenso_gdpr_version", nullable = false)
    private String consensoGdprVersion;

    @Column(name = "anonimizzato", nullable = false)
    private boolean anonimizzato;

}
