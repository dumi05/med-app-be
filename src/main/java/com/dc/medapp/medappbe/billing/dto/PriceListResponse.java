package com.dc.medapp.medappbe.billing.dto;

import com.dc.medapp.medappbe.billing.PriceListEntity;

import java.math.BigDecimal;

public record PriceListResponse(
        Long id,
        Long specializationId,
        String specializationName,
        String visitType,
        BigDecimal price
) {
    public static PriceListResponse fromEntity(PriceListEntity entity){
        return new PriceListResponse(
                entity.getId(),
                entity.getSpecialization().getId(),
                entity.getSpecialization().getName(),
                entity.getVisitType(),
                entity.getPrice()
        );
    }
}
