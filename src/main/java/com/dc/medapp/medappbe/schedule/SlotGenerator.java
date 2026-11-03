package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.doctor.DoctorEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static com.dc.medapp.medappbe.schedule.ScheduleService.MAX_SLOT_MINUTES;

@Service
public class SlotGenerator {

    private final AvailabilitySlotRepository slotRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final int slotMinutes;
    private final int horizonWeeks;

    public SlotGenerator(AvailabilitySlotRepository slotRepository,
                         WorkingHoursRepository workingHoursRepository,
                         @Value("${app.schedule.slot-minutes:30}") int slotMinutes,
                         @Value("${app.schedule.horizon-weeks:8}") int horizonWeeks){
        this.slotRepository = slotRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.horizonWeeks = horizonWeeks;
        this.slotMinutes = slotMinutes;
    }

    public int slotMinutes(){
        return slotMinutes;
    }

    @Transactional
    public int generate(DoctorEntity doc, LocalDateTime now) {
        var bands = workingHoursRepository.findByDoctorId(doc.getId());
        if(bands.isEmpty()){
            return 0;
        }
        var firstDay = now.toLocalDate();
        var lastDay = firstDay.plusWeeks(horizonWeeks).minusDays(1);
        var existing = new ArrayList<>(
                slotRepository.findByDoctorIdAndStartTimeBetweenOrderByStartTime(
                        doc.getId(),
                        firstDay.atStartOfDay().minusMinutes(MAX_SLOT_MINUTES),
                        lastDay.atTime(LocalTime.MAX))
        );
        var created = new ArrayList<AvailabilitySlotEntity>();
        for(LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)){
            if(day.getDayOfWeek() == DayOfWeek.SUNDAY){
                continue;
            }
            for(var band : bands){
                if(band.getDayOfWeek() != day.getDayOfWeek()){
                    continue;
                }
                for(var start = day.atTime(band.getStartTime());
                    !start.plusMinutes(slotMinutes).isAfter(day.atTime(band.getEndTime()));
                    start = start.plusMinutes(slotMinutes)){

                    var end = start.plusMinutes(slotMinutes);
                    if(!start.isAfter(now) || overlapsAny(existing, start, end)){
                        continue;
                    }
                    var slot = AvailabilitySlotEntity.builder()
                            .doctor(doc)
                            .startTime(start)
                            .durationMinutes(slotMinutes)
                            .booked(false)
                            .generated(true)
                            .build();

                    created.add(slot);
                    existing.add(slot);
                }
            }
        }
        slotRepository.saveAll(created);
        return created.size();
    }

    private boolean overlapsAny(List<AvailabilitySlotEntity> slots, LocalDateTime start, LocalDateTime end){
        return slots.stream().anyMatch(slot -> slot.overlaps(start, end));
    }
}
