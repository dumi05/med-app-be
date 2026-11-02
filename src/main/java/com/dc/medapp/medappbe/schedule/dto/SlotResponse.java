package com.dc.medapp.medappbe.schedule.dto;

import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SlotResponse(
        Long id,
        Long doctorId,
        String doctorFirstName,
        String doctorLastName,
        LocalDateTime startTime,
        int durationMinutes,
        boolean booked,
        BigDecimal price) {
    public static SlotResponse fromEntity(AvailabilitySlotEntity entity) {
        return fromEntity(entity, null);
    }
    public static SlotResponse fromEntity(AvailabilitySlotEntity entity, BigDecimal price) {
        return new SlotResponse(
                entity.getId(),
                entity.getDoctor().getId(),
                entity.getDoctor().getFirstName(),
                entity.getDoctor().getLastName(),
                entity.getStartTime(),
                entity.getDurationMinutes(),
                entity.isBooked(),
                price
        );
    }
}
