package com.dc.medapp.medappbe.leaves.dto;

import com.dc.medapp.medappbe.leaves.DoctorLeaveEntity;

import java.time.LocalDate;

public record LeaveResponse(
        Long id,
        Long doctorId,
        LocalDate startDate,
        LocalDate endDate,
        Long replacementDoctorId,
        String replacementDoctorName) {
    public static LeaveResponse fromEntity(DoctorLeaveEntity entity){
        return new LeaveResponse(
                entity.getId(),
                entity.getDoctor().getId(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getReplacementDoctor() == null ? null : entity.getReplacementDoctor().getId(),
                entity.getReplacementDoctor() == null ? null : entity.getReplacementDoctor().getFirstName() + " " + entity.getReplacementDoctor().getLastName()
        );
    }
}
