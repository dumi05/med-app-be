package com.dc.medapp.medappbe.schedule.dto;

import com.dc.medapp.medappbe.schedule.WorkingHoursEntity;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record WorkingHoursBand(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
        ) {
    public static WorkingHoursBand fromEntity(WorkingHoursEntity entity){
        return new WorkingHoursBand(entity.getDayOfWeek(), entity.getStartTime(), entity.getEndTime());
    }
}
