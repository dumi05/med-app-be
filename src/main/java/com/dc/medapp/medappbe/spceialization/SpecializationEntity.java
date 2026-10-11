package com.dc.medapp.medappbe.spceialization;

import com.dc.medapp.medappbe.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "specialization")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SpecializationEntity extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String name;
}
