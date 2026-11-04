package com.dc.medapp.medappbe.leaves.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LeavePreviewResponse(
        long daysOfNotice,
        boolean shortNotice,
        List<AffectedAppointment> appointments,
        int reassign,
        int cancel,
        String newsTitle,
        String newsBody
) {
    public enum Outcome { REASSIGN, CANCEL }

    public record AffectedAppointment(Long id, LocalDateTime startTime, String patientFirstName, String patientLastName, Outcome outcome){}
}
