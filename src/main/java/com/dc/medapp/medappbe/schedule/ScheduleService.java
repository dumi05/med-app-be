package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.SlotOverlapException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.security.ProfileResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    public static final int MIN_SLOT_MINUTES = 5;
    public static final int MAX_SLOT_MINUTES = 240;
    public static final int MAX_SLOTS_PER_REQUEST = 48;
    public static final int MAX_SEARCH_DAYS = 62;

    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final DoctorRepository doctorRepository;
    private final ProfileResolver profileResolver;

    @Transactional(readOnly = true)
    public List<AvailabilitySlotEntity> searchAvailability(Long specId, Long docId, LocalDate from, LocalDate to) {
        checkRange(from, to);
        var start = from.atStartOfDay();
        var end = to.atTime(LocalTime.MAX);
        var now = LocalDateTime.now();
        return docId == null
                ? availabilitySlotRepository.findAvailableSlotsBySpecializationIdAndDateRange(specId, start, end, now)
                : availabilitySlotRepository.findAvailableByDoctor(specId, docId, start, end, now);
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlotEntity> findMySlots(UserEntity user, LocalDate from, LocalDate to) {
        checkRange(from, to);
        return availabilitySlotRepository.findByDoctorIdAndStartTimeBetweenOrderByStartTime(profileResolver.doctorIdOf(user), from.atStartOfDay(), to.atTime(LocalTime.MAX));
    }

    @Transactional
    public AvailabilitySlotEntity createAvailabilitySlotAs(UserEntity actor, Long requestDoctorId, LocalDateTime startTime, int durationMinutes){
        checkDuration(durationMinutes);
        if(!startTime.isAfter(LocalDateTime.now())){
            throw new IllegalArgumentException("L'inizio deve essere nel futuro.");
        }
        var doctor = resolveDoctor(actor, requestDoctorId);
        assertNoOverlap(doctor.getId(), startTime, startTime.plusMinutes(durationMinutes));
        return availabilitySlotRepository.save(newSlot(doctor, startTime, durationMinutes));
    }

    @Transactional
    public List<AvailabilitySlotEntity> createAvailabilitySlotsAs(UserEntity actor, Long requestDoctorId, LocalDate date, LocalTime startTime, LocalTime endTime, int slotMinutes){
        checkDuration(slotMinutes);
        if(!endTime.isAfter(startTime)){
            throw new IllegalArgumentException("L'orario di fine deve essere successivo a quello di inizio.");
        }
        var first = date.atTime(startTime);
        if(!first.isAfter(LocalDateTime.now())){
            throw new IllegalArgumentException("L'inizio deve essere nel futuro.");
        }
        var count = (int) (ChronoUnit.MINUTES.between(startTime, endTime) / slotMinutes);
        if(count == 0) {
            throw new IllegalArgumentException("La fascia oraria è più corta di una visita.");
        }
        if (count > MAX_SLOTS_PER_REQUEST) {
            throw new IllegalArgumentException("Al massimo " + MAX_SLOTS_PER_REQUEST + " slot per richiesta.");
        }
        var doctor = resolveDoctor(actor, requestDoctorId);
         var starts = new ArrayList<LocalDateTime>();
         for(int i = 0; i < count; i++) {
             starts.add(first.plusMinutes((long) i * slotMinutes));
         }
         assertNoOverlap(doctor.getId(), first, first.plusMinutes((long) count * slotMinutes));
         return availabilitySlotRepository.saveAll(starts.stream().map(s -> newSlot(doctor, s, slotMinutes)).toList());
    }

    private AvailabilitySlotEntity newSlot(DoctorEntity doctor, LocalDateTime startTime, int durationMinutes) {
        return AvailabilitySlotEntity.builder()
                .startTime(startTime)
                .durationMinutes(durationMinutes)
                .booked(false)
                .doctor(doctor)
                .build();
    }

    private DoctorEntity resolveDoctor(UserEntity actor, Long requestDoctorId) {
        Long doctorId = switch (actor.getRole()) {
            case ADMIN -> {
                if(requestDoctorId == null){
                    throw new IllegalArgumentException("L'amministratore deve indicare un medico.");
                }
                yield requestDoctorId;
            }
            case DOCTOR -> {
                var ownId = profileResolver.doctorIdOf(actor);
                if(requestDoctorId != null && !requestDoctorId.equals(ownId)){
                    throw new UnauthorizedAccessException("Un medico può creare slot solo per sé stesso.");
                }
                yield ownId;
            }
            default -> throw new UnauthorizedAccessException("Ruolo non abilitato alla creazione di slot.");
        };
        return doctorRepository.findById(doctorId).orElseThrow(() -> new ResourceNotFoundException("Medico " + doctorId + " non trovato."));
    }

    private void assertNoOverlap(Long doctorId, LocalDateTime startTime, LocalDateTime endTime) {
        var candidates = availabilitySlotRepository.findByDoctorIdAndStartTimeBetweenOrderByStartTime(doctorId, startTime.minusMinutes(MAX_SLOT_MINUTES), endTime);
        if(candidates.stream().anyMatch(existing -> existing.overlaps(startTime, endTime))){
            throw new SlotOverlapException("Esistono già slot in quella fascia oraria.");
        }
    }

    private void checkDuration(int minutes){
        if(minutes < MIN_SLOT_MINUTES || minutes > MAX_SLOT_MINUTES) {
            throw new IllegalArgumentException("La durata deve essere tra " + MIN_SLOT_MINUTES + " minuti e " + MAX_SLOT_MINUTES + " minuti.");
        }
    }

    private void checkRange(LocalDate from, LocalDate to) {
        if(to.isBefore(from)) {
            throw new IllegalArgumentException("La data finale non può precedere quella iniziale.");
        }
        if(ChronoUnit.DAYS.between(from, to) + 1 > MAX_SEARCH_DAYS){
            throw new IllegalArgumentException("L'intervallo può durare al massimo "+ MAX_SEARCH_DAYS + " giorni.");
        }
    }
}
