package com.dc.medapp.medappbe.appointments;

import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.SlotNotAvailableException;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final PatientRepository patientRepository;
    private final NoShowRiskCalculator noShowRiskCalculator;

    @Transactional
    public AppointmentEntity createAppointment(Long patientId, Long slotId) {
        var patient = patientRepository.findById(patientId).orElseThrow(() -> new ResourceNotFoundException("Paziente " + patientId + " non trovato"));
        var slot = availabilitySlotRepository.findById(slotId).orElseThrow(() -> new ResourceNotFoundException("Slot " + slotId + " non trovato"));

        if(slot.isBooked()){
            throw new SlotNotAvailableException("Slot già prenotato.");
        }
        slot.setBooked(true);

        return appointmentRepository.save(AppointmentEntity
                .builder()
                .status(AppointmentStatus.BOOKED)
                .patient(patient)
                .slot(slot)
                .noShowRisk(noShowRiskCalculator.calculateRisk(patientId))
                .build());
    }

    @Transactional
    public AppointmentEntity completeVisit(Long appointmentId) {
        var appointment = findOrThrowAppointment(appointmentId);
        requiredStatus(appointment, AppointmentStatus.BOOKED, "completare");
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setCompletedAt(Instant.now());
        return appointment;
    }

    @Transactional
    public AppointmentEntity cancelAppointment(Long appointmentId) {
        var appointment = findOrThrowAppointment(appointmentId);
        requiredStatus(appointment, AppointmentStatus.BOOKED, "cancellare");
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(Instant.now());
        appointment.getSlot().setBooked(false);
        return appointment;
    }

    private void requiredStatus(AppointmentEntity appointment, AppointmentStatus expectedStatus, String action) {
        if(appointment.getStatus() != expectedStatus){
            throw new InvalidStateTransitionException("Impossibile " + action + " un appuntamento in stato " + appointment.getStatus());
        }
    }

    private AppointmentEntity findOrThrowAppointment(Long appointmentId) {
        return appointmentRepository.findById(appointmentId).orElseThrow(() -> new ResourceNotFoundException("Appuntamento non trovato"));
    }
}
