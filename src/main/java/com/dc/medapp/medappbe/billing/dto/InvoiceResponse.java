package com.dc.medapp.medappbe.billing.dto;

import com.dc.medapp.medappbe.billing.InvoiceItemEntity;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceResponse(Long appointmentId, String number, BigDecimal amount, Instant issuedAt, Instant paidAt) {
    public static InvoiceResponse fromEntity(InvoiceItemEntity entity){
        return new InvoiceResponse(
                entity.getAppointment().getId(),
                entity.getNumber(),
                entity.getAmount(),
                entity.getIssuedAt(),
                entity.getPaidAt()
        );
    }
}
