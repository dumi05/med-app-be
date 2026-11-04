package com.dc.medapp.medappbe.appointments;

import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.schedule.AvailabilitySlotEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AppointmentReassignmentService {

    @Transactional
    @Audited(action = "REASSIGN_APPOINTMENT", entity = "Appointment")
    public AppointmentEntity reassign(AppointmentEntity appointment, AvailabilitySlotEntity slot){
        var oldSlot = appointment.getSlot();
        appointment.setReassignedFrom(oldSlot.getDoctor());
        appointment.setReassignedAt(Instant.now());
        oldSlot.setBooked(false);
        slot.setBooked(true);
        appointment.setSlot(slot);
        return appointment;
    }

    @Transactional
    @Audited(action = "CANCEL_APPOINTMENT_ABSENCE", entity = "Appointment")
    public AppointmentEntity cancelBecauseDoctorIsAway(AppointmentEntity appointment){
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(Instant.now());
        appointment.setCancellationReason(CancellationReason.DOCTOR_ABSENCE);
        appointment.getSlot().setBooked(false);
        return appointment;
    }
}
