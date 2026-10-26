package com.dc.medapp.medappbe.schedule.dto;

import com.dc.medapp.medappbe.schedule.ScheduleService;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record SlotRequest(Long doctorId,
                          @NotNull @Future LocalDateTime startTime,
                          @Min(ScheduleService.MIN_SLOT_MINUTES) @Max(ScheduleService.MAX_SLOT_MINUTES) int durationMinutes){
}
