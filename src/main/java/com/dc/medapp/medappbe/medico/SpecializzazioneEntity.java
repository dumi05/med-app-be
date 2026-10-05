package com.dc.medapp.medappbe.medico;

import com.dc.medapp.medappbe.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "specializzazione")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SpecializzazioneEntity extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String nome;
}
