package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.common.BaseEntity;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "doctor_delegation")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DoctorDelegationEntity extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "delegating_doctor_id", nullable = false)
    private DoctorEntity delegatingDoctor;

    @ManyToOne(optional = false)
    @JoinColumn(name = "delegate_doctor_id", nullable = false)
    private DoctorEntity delegateDoctor;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DelegationStatus status;

}
