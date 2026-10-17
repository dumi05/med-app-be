package com.dc.medapp.medappbe.delegations.dto;

import com.dc.medapp.medappbe.delegations.DelegationStatus;
import com.dc.medapp.medappbe.delegations.DoctorDelegationEntity;

import java.time.LocalDate;

public record DoctorDelegationResponse(
        Long id,
        Long delegatingDoctorId,
        String delegatingDoctorFirstName,
        String delegatingDoctorLastName,
        Long delegateDoctorId,
        String delegateDoctorFirstName,
        String delegateDoctorLastName,
        LocalDate startDate,
        LocalDate endDate,
        DelegationStatus status
){
    public static DoctorDelegationResponse fromEntity(DoctorDelegationEntity entity) {
        return new DoctorDelegationResponse(
                entity.getId(),
                entity.getDelegatingDoctor().getId(),
                entity.getDelegatingDoctor().getFirstName(),
                entity.getDelegatingDoctor().getLastName(),
                entity.getDelegateDoctor().getId(),
                entity.getDelegateDoctor().getFirstName(),
                entity.getDelegateDoctor().getLastName(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getStatus()
        );
    }
}
