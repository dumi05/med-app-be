package com.dc.medapp.medappbe.doctor;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.common.BaseEntity;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "doctor")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DoctorEntity extends BaseEntity {
    @OneToOne(optional = false)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    private String firstName;
    private String lastName;

    @ManyToOne(optional = false)
    @JoinColumn(name = "specialization_id")
    private SpecializationEntity specialization;

    @Column(name = "delegation_consent", nullable = false)
    private boolean delegationConsent;
}
