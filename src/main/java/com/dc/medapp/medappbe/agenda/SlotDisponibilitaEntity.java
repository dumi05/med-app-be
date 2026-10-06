package com.dc.medapp.medappbe.agenda;

import com.dc.medapp.medappbe.common.BaseEntity;
import com.dc.medapp.medappbe.medico.MedicoEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "slot_disponibilita")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SlotDisponibilitaEntity extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "medico_id")
    private MedicoEntity medico;

    @Column(name = "data_ora", nullable = false)
    private LocalDateTime dataOra;

    @Column(name = "durata_minuti", nullable = false)
    private int durataMinuti;

    @Column(name = "occupato", nullable = false)
    private boolean occupato;
}
