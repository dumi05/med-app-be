package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.schedule.dto.WorkingHoursBand;
import com.dc.medapp.medappbe.schedule.dto.WorkingHoursResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkingHoursService {

    public static final int MAX_BANDS_PER_DAY = 3;

    /** What a new doctor gets: Monday to Friday 9-13 and 15-19, Saturday 9-13. The doctor changes it afterwards. */
    public static final List<WorkingHoursBand> DEFAULT_BANDS = List.of(
            band(DayOfWeek.MONDAY, 9, 13), band(DayOfWeek.MONDAY, 15, 19),
            band(DayOfWeek.TUESDAY, 9, 13), band(DayOfWeek.TUESDAY, 15, 19),
            band(DayOfWeek.WEDNESDAY, 9, 13), band(DayOfWeek.WEDNESDAY, 15, 19),
            band(DayOfWeek.THURSDAY, 9, 13), band(DayOfWeek.THURSDAY, 15, 19),
            band(DayOfWeek.FRIDAY, 9, 13), band(DayOfWeek.FRIDAY, 15, 19),
            band(DayOfWeek.SATURDAY, 9, 13));

    private final WorkingHoursRepository workingHoursRepository;
    private final AvailabilitySlotRepository slotRepository;
    private final DoctorRepository doctorRepository;
    private final ScheduleService scheduleService;
    private final SlotGenerator slotGenerator;

    @Transactional(readOnly = true)
    public List<WorkingHoursEntity> find(UserEntity actor, Long requestDoctorId) {
        var doctor = scheduleService.resolveDoctor(actor, requestDoctorId);
        return workingHoursRepository.findSortedByDoctorId(doctor.getId());
    }

    /**
     * Replaces all the working hours of a doctor and brings the slots in line: the free slots that came from the old hours
     * (and are still to come) are taken away, the new ones are created. Booked slots, slots made by hand and slots
     * tied to a cancelled visit are never touched. An empty list means "no working hours".
     */
    @Transactional
    @Audited(action = "UPDATE_WORKING_HOURS", entity = "WorkingHours", idArg = 1)
    public WorkingHoursResponse replace(UserEntity actor, Long requestDoctorId, List<WorkingHoursBand> bands) {
        var doctor = scheduleService.resolveDoctor(actor, requestDoctorId);
        return replaceFor(doctor, bands);
    }

    /** A new doctor starts with the usual hours, and so with slots to book. */
    @Transactional
    public void applyDefaults(DoctorEntity doctor) {
        replaceFor(doctor, DEFAULT_BANDS);
    }

    private WorkingHoursResponse replaceFor(DoctorEntity doctor, List<WorkingHoursBand> bands) {
        validate(bands, slotGenerator.slotMinutes());
        var now = LocalDateTime.now();

        workingHoursRepository.deleteByDoctorId(doctor.getId());
        workingHoursRepository.flush();
        workingHoursRepository.saveAll(bands.stream().map(band -> WorkingHoursEntity.builder()
                .doctor(doctor).dayOfWeek(band.dayOfWeek()).startTime(band.startTime()).endTime(band.endTime()).build()).toList());
        var removed = slotRepository.deleteFreeGeneratedFrom(doctor.getId(), now);
        // the bulk delete clears the persistence context: the doctor is read again
        var created = slotGenerator.generate(doctorRepository.findById(doctor.getId()).orElseThrow(), now);

        var saved = workingHoursRepository.findSortedByDoctorId(doctor.getId()).stream()
                .map(WorkingHoursBand::fromEntity).toList();
        return new WorkingHoursResponse(doctor.getId(), saved, created, removed);
    }

    private static void validate(List<WorkingHoursBand> bands, int slotMinutes) {
        for (var band : bands) {
            if (band.dayOfWeek() == DayOfWeek.SUNDAY) {
                throw new IllegalArgumentException("La domenica non è un giorno di lavoro.");
            }
            if (!band.endTime().isAfter(band.startTime())) {
                throw new IllegalArgumentException("In ogni fascia la fine deve essere dopo l'inizio.");
            }
            if (ChronoUnit.MINUTES.between(band.startTime(), band.endTime()) < slotMinutes) {
                throw new IllegalArgumentException("Ogni fascia deve durare almeno " + slotMinutes + " minuti.");
            }
            if (isNotOnFiveMinutes(band.startTime()) || isNotOnFiveMinutes(band.endTime())) {
                throw new IllegalArgumentException("Gli orari devono essere a multipli di 5 minuti.");
            }
        }
        for (var day : DayOfWeek.values()) {
            var ofTheDay = bands.stream().filter(band -> band.dayOfWeek() == day)
                    .sorted(Comparator.comparing(WorkingHoursBand::startTime)).toList();
            if (ofTheDay.size() > MAX_BANDS_PER_DAY) {
                throw new IllegalArgumentException("Al massimo " + MAX_BANDS_PER_DAY + " fasce per giorno.");
            }
            for (int i = 1; i < ofTheDay.size(); i++) {
                if (ofTheDay.get(i).startTime().isBefore(ofTheDay.get(i - 1).endTime())) {
                    throw new IllegalArgumentException("Due fasce dello stesso giorno non possono sovrapporsi.");
                }
            }
        }
    }

    private static boolean isNotOnFiveMinutes(LocalTime time) {
        return time.getMinute() % 5 != 0 || time.getSecond() != 0 || time.getNano() != 0;
    }

    private static WorkingHoursBand band(DayOfWeek day, int fromHour, int toHour) {
        return new WorkingHoursBand(day, LocalTime.of(fromHour, 0), LocalTime.of(toHour, 0));
    }
}