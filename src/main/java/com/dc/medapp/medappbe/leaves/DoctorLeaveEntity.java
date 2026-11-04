package com.dc.medapp.medappbe.leaves;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.common.AuditableEntity;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "doctor_leave")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorLeaveEntity extends AuditableEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorEntity doctor;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @ManyToOne
    @JoinColumn(name = "replacement_doctor_id")
    private DoctorEntity replacementDoctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_role", nullable = false)
    private Role createdByRole;
}
