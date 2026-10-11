package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.security.ProfileResolver;
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
    private final ProfileResolver profileResolver;

    private AvailabilitySlotEntity createAvailabilitySlot(Long doctorId, LocalDateTime startTime, int durationMinutes) {
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

    public AvailabilitySlotEntity createAvailabilitySlotAs(UserEntity actor, Long requestedDoctorId, LocalDateTime startTime, int durationMinutes) {
        Long doctorId = switch (actor.getRole()) {
            case ADMIN -> {
                if(requestedDoctorId == null) {
                    throw new IllegalArgumentException("L'amministratore deve indicare il medico.");
                }
                yield requestedDoctorId;
            }
            case DOCTOR -> {
                var ownId = profileResolver.doctorIdOf(actor);
                if(requestedDoctorId != null && !requestedDoctorId.equals(ownId)){
                    throw new UnauthorizedAccessException("Un medico può creare slot solo per sé");
                }
                yield ownId;
            }
            default -> throw new UnauthorizedAccessException("Ruolo non autorizzato a creare slot.");
        };
        return createAvailabilitySlot(doctorId, startTime, durationMinutes);
    }
}
