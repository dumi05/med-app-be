package com.dc.medapp.medappbe.medico;

import com.dc.medapp.medappbe.auth.User;
import com.dc.medapp.medappbe.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "medico")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MedicoEntity extends BaseEntity {
    @OneToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private String nome;
    private String cognome;

    @ManyToOne(optional = false)
    @JoinColumn(name = "specializzazione_id")
    private SpecializzazioneEntity specializzazione;

    @Column(name = "consenso_delega", nullable = false)
    private boolean consensoDelega;
}
