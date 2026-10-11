package com.dc.medapp.medappbe.spceialization.dto;

import com.dc.medapp.medappbe.spceialization.SpecializationEntity;

public record SpecializationResponse(Long id, String name) {
    public static SpecializationResponse fromEntity(SpecializationEntity entity) {
        return new SpecializationResponse(entity.getId(), entity.getName());
    }
}
