package com.dc.medapp.medappbe.schedule.dto;

import java.util.List;

public record WorkingHoursResponse(
        Long doctorId,
        List<WorkingHoursBand> bands,
        int createdSlots,
        int removedSlots
) {
}
