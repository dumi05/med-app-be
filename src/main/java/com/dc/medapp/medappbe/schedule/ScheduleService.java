package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final DoctorRepository doctorRepository;

    public AvailabilitySlotEntity createAvailabilitySlot(Long doctorId, LocalDateTime startTime, int durationMinutes) {
        var doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Medico non trovato"));
        var slot = AvailabilitySlotEntity.builder()
                .startTime(startTime)
                .durationMinutes(durationMinutes)
                .doctor(doctor)
                .booked(false)
                .build();
        return availabilitySlotRepository.save(slot);
    }

    public List<AvailabilitySlotEntity> searchAvailability(Long specializationId, LocalDate date) {
        LocalDateTime dateStart = date.atStartOfDay();
        LocalDateTime dateEnd = date.atTime(LocalTime.MAX);
        return availabilitySlotRepository.findAvailableSlotsBySpecializationIdAndDateRange(specializationId, dateStart, dateEnd);
    }
}
