package com.dc.medapp.medappbe.leaves;

import com.dc.medapp.medappbe.schedule.WorkingHoursEntity;
import com.dc.medapp.medappbe.schedule.WorkingHoursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReturnDayFinder {

    private static final int DAYS_TO_LOOK_AHEAD = 60;

    private final WorkingHoursRepository workingHoursRepository;
    private final DoctorLeaveRepository doctorLeaveRepository;

    public Optional<LocalDate> after(Long docId, LocalDate lastDayAway){
        var workingDays = workingHoursRepository.findByDoctorId(docId).stream().map(WorkingHoursEntity::getDayOfWeek).toList();
        return firstWorkingDay(docId, lastDayAway, workingDays);
    }

    private Optional<LocalDate> firstWorkingDay(Long docId, LocalDate lastDayAway, List<DayOfWeek> workingDays){
        for (int i = 1; i <= DAYS_TO_LOOK_AHEAD; i++) {
            var day = lastDayAway.plusDays(i);
            if(day.getDayOfWeek() != DayOfWeek.SUNDAY &&
                    workingDays.contains(day.getDayOfWeek()) &&
                    !doctorLeaveRepository.isAway(docId, day)){
                return Optional.of(day);
            }
        }
        return Optional.empty();
    }
}
