package com.dc.medapp.medappbe.appointments;

import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.common.exception.*;
import com.dc.medapp.medappbe.delegations.DoctorDelegationService;
import com.dc.medapp.medappbe.leaves.DoctorLeaveRepository;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotRepository;
import com.dc.medapp.medappbe.security.ProfileResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final PatientRepository patientRepository;
    private final NoShowRiskCalculator noShowRiskCalculator;
    private final DoctorDelegationService delegationService;
    private final ProfileResolver profileResolver;
    private final DoctorLeaveRepository leaveRepository;

    @Transactional
    @Audited(action = "BOOK_APPOINTMENT", entity = "Appointment")
    public AppointmentEntity createAppointment(Long patientId, Long slotId) {
        return book(patientId, slotId, Role.PATIENT, null);
    }

    @Transactional
    @Audited(action = "BOOK_APPOINTMENT", entity = "Appointment")
    public AppointmentEntity createAppointmentAs(UserEntity actor, Long slotId, Long requestedPatientId){
        if(actor.getRole() == Role.PATIENT){
            var ownId = profileResolver.patientIdOf(actor);
            if(requestedPatientId != null && !requestedPatientId.equals(ownId)){
                throw new UnauthorizedAccessException("Un paziente può prenotare solo per sé.");
            }
            return book(ownId, slotId, Role.PATIENT, null);
        }
        if(requestedPatientId == null){
            throw new IllegalArgumentException("Indica il paziente per cui prenotare.");
        }
        var requesterDoctorId = actor.getRole() == Role.DOCTOR ? profileResolver.doctorIdOf(actor) : null;
        return book(requestedPatientId, slotId, actor.getRole(), requesterDoctorId);
    }

    private AppointmentEntity book(Long patientId, Long slotId, Role bookedBy, Long requesterDoctorId) {
        var patient = patientRepository.findById(patientId)
                .filter(found -> !found.isAnonymized())
                .orElseThrow(() -> new ResourceNotFoundException("Paziente " + patientId + " non trovato"));
        var slot = availabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot " + slotId + " non trovato"));

        if (leaveRepository.isAway(slot.getDoctor().getId(), slot.getStartTime().toLocalDate())) {
            throw new SlotNotAvailableException("Il medico è assente in quel giorno.");
        }
        if(requesterDoctorId != null && !delegationService.isAuthorized(requesterDoctorId, slot.getDoctor().getId())){
            throw new UnauthorizedAccessException("Il medico può prenotare solo sui propri slot.");
        }
        if(slot.isBooked()){
            throw new SlotNotAvailableException("Slot già prenotato.");
        }
        if(slot.getStartTime().isBefore(LocalDateTime.now())){
            throw new SlotNotAvailableException("Slot già trascorso.");
        }
        assertPatientIsFree(patientId, slot);
        slot.setBooked(true);

        return appointmentRepository.save(AppointmentEntity
                .builder()
                .status(AppointmentStatus.BOOKED)
                .patient(patient)
                .slot(slot)
                .noShowRisk(noShowRiskCalculator.calculateRisk(patientId))
                .bookedByRole(bookedBy)
                .build());
    }

    @Transactional
    @Audited(action = "COMPLETE_VISIT", entity = "Appointment", idArg = 0)
    public AppointmentEntity completeVisit(Long appointmentId, Long doctorId) {
        var appointment = findOrThrowAppointment(appointmentId);
        var assignedDoctorId = appointment.getSlot().getDoctor().getId();
        if(!delegationService.isAuthorized(doctorId, assignedDoctorId)){
            throw new UnauthorizedAccessException("Il medico non è assegnato a questo appuntamento.");
        }
        requiredStatus(appointment, AppointmentStatus.BOOKED, "completare");
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setCompletedAt(Instant.now());
        return appointment;
    }

    @Transactional
    @Audited(action = "CANCEL_APPOINTMENT", entity = "Appointment", idArg = 0)
    public AppointmentEntity cancelAppointment(Long appointmentId, Long patientId) {
        var appointment = findOrThrowAppointment(appointmentId);
        if(!appointment.getPatient().getId().equals(patientId)) {
            throw new UnauthorizedAccessException("L'appuntamento appartiene ad un altro paziente.");
        }
        requiredStatus(appointment, AppointmentStatus.BOOKED, "cancellare");
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(Instant.now());
        appointment.getSlot().setBooked(false);
        appointment.setCancellationReason(CancellationReason.PATIENT);
        appointment.setCancelledAt(Instant.now());
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

    @Transactional(readOnly = true)
    public List<AppointmentEntity> findByPatient(Long patientId) {
        return appointmentRepository.findByPatientIdOrderBySlotStartTimeAsc(patientId);
    }

    @Transactional(readOnly = true)
    public List<AppointmentEntity> findByDoctor(Long doctorId) {
        var doctorIds = new ArrayList<Long>();
        doctorIds.add(doctorId);
        doctorIds.addAll(delegationService.activeDelegatingDoctorIds(doctorId));
        return appointmentRepository.findBySlotDoctorIdInOrderBySlotStartTimeAsc(doctorIds);
    }

    private void assertPatientIsFree(Long patientId, AvailabilitySlotEntity slot) {
        var clash = appointmentRepository.findByPatientIdAndStatus(patientId, AppointmentStatus.BOOKED).stream()
                .anyMatch(booked -> booked.getSlot().overlaps(slot.getStartTime(), slot.endTime()));
        if (clash) {
            throw new AppointmentOverlapException("Hai già una prenotazione in quella fascia oraria.");
        }
    }
}
