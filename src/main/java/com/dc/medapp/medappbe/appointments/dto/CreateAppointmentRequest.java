package com.dc.medapp.medappbe.appointments.dto;

import jakarta.validation.constraints.NotNull;

public record CreateAppointmentRequest(
        @NotNull Long slotId,
        Long patientId
) {
    public CreateAppointmentRequest(Long slotId) {
        this(slotId, null);
    }
}
