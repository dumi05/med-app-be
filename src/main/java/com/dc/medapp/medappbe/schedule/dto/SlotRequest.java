package com.dc.medapp.medappbe.schedule.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record SlotRequest(@NotNull Long doctorId,
                          @NotNull @Future LocalDateTime startTime,
                          @Positive int durationMinutes) {
}
