package com.dc.medapp.medappbe.schedule.dto;

import com.dc.medapp.medappbe.schedule.ScheduleService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record BulkSlotRequest(
        Long doctorId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(ScheduleService.MIN_SLOT_MINUTES) @Max(ScheduleService.MAX_SLOT_MINUTES) int slotMinutes
        ) {
}
