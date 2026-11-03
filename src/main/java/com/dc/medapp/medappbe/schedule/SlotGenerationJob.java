package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.doctor.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlotGenerationJob {
    private final WorkingHoursRepository workingHoursRepository;
    private final DoctorRepository doctorRepository;
    private final SlotGenerator slotGenerator;

    @Scheduled(cron = "${app.schedule.generation.cron:0 0 2 * * *}")
    public void run() {
        int total = 0;
        for(var doctorId : workingHoursRepository.findDoctorIdsWithWorkingHours()){
            var doc = doctorRepository.findById(doctorId).orElse(null);
            if(doc != null && doc.getUser().isEnabled()){
                total += slotGenerator.generate(doc, LocalDateTime.now());
            }
        }
        log.info("Slot generation: {} new slots", total);
    }
}
