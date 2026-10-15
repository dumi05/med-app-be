package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MedicalReportService {
    private final MedicalReportRepository medicalReportRepository;
    private final AppointmentRepository appointmentRepository;
    private final BillingService billingService;

    @Transactional
    public MedicalReportEntity createReport(UserEntity user, Long appointmentId, String diagnosis, String attachmentUrl) {
        var appointment = findAppointmentOrThrow(appointmentId);

        if(!isAssignedDoctor(appointment, user)){
            throw new UnauthorizedAccessException("Solo il medico dell'appuntamento può scrivere il referto.");
        }

        if(appointment.getStatus() != AppointmentStatus.COMPLETED){
            throw new InvalidStateTransitionException("Impossibile redigere il report in stato " + appointment.getStatus());
        }

        var report = MedicalReportEntity.builder()
                .appointment(appointment)
                .attachmentUrl(attachmentUrl)
                .diagnosisText(diagnosis)
                .completedAt(Instant.now())
                .build();

        var result = medicalReportRepository.save(report);

        appointment.setStatus(AppointmentStatus.REPORTED);
        appointment.setReportedAt(Instant.now());

        billingService.issueInvoiceItem(appointment);

        return result;
    }

    @Transactional(readOnly = true)
    public MedicalReportEntity getReport(UserEntity user, Long reportId) {
        var report = medicalReportRepository.findById(reportId).orElseThrow(() -> new ResourceNotFoundException("Report non trovato: " + reportId));
        checkReadAccess(report.getAppointment(), user);
        return report;
    }

    @Transactional(readOnly = true)
    public MedicalReportEntity getReportByAppointment(UserEntity user, Long appointmentId) {
        var appointment = findAppointmentOrThrow(appointmentId);
        checkReadAccess(appointment, user);
        return medicalReportRepository.findByAppointmentId(appointmentId).orElseThrow(() -> new ResourceNotFoundException("Nessun referto trovato per l'appuntamento: " + appointmentId));
    }

    private void checkReadAccess(AppointmentEntity appointment, UserEntity user) {
        var isOwnerPatient = user.getRole() == Role.PATIENT && appointment.getPatient().getUser().getId().equals(user.getId());

        if(!isOwnerPatient && !isAssignedDoctor(appointment, user)){
            throw new UnauthorizedAccessException("Non hai accesso a questo referto.");
        }
    }

    private boolean isAssignedDoctor(AppointmentEntity appointment, UserEntity user) {
        return user.getRole() == Role.DOCTOR && appointment.getSlot().getDoctor().getUser().getId().equals(user.getId());
    }

    private AppointmentEntity findAppointmentOrThrow(Long appointmentId) {
        return this.appointmentRepository.findById(appointmentId).orElseThrow(() -> new ResourceNotFoundException("Appuntamento non presente."));
    }
}
