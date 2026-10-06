package com.dc.medapp.medappbe.agenda.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record SlotRequest(@NotNull Long medicoId, @NotNull @Future LocalDateTime dataOra, @Positive int durataMinuti) {
}
