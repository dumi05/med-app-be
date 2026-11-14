package com.dc.medapp.medappbe.appointments;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class NoShowRiskCalculator {
    private static final double RISK_THRESHOLD = 0.5;
    private final AppointmentRepository appointmentRepository;

    public boolean calculateRisk(Long patientId){
        var pastAppointments = appointmentRepository.findPastNotCancelledByPatientId(patientId, LocalDateTime.now());

        if(pastAppointments.isEmpty()){
            return false; // new patient, no risk
        }

        long missed = pastAppointments.stream().filter(app -> app.getStatus() == AppointmentStatus.BOOKED).count();

        return (double) missed / pastAppointments.size() >= RISK_THRESHOLD;
    }
}
