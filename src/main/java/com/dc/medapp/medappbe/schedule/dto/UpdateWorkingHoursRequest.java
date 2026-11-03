package com.dc.medapp.medappbe.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateWorkingHoursRequest(Long doctorId,
                                        @NotNull @Size(max = 24) List<@Valid @NotNull WorkingHoursBand> bands) {
}
