package com.dc.medapp.medappbe.doctor.dto;

import com.dc.medapp.medappbe.doctor.SpecializationEntity;

public record SpecializationResponse(Long id, String name) {
    public static SpecializationResponse fromEntity(SpecializationEntity entity) {
        return new SpecializationResponse(entity.getId(), entity.getName());
    }
}
