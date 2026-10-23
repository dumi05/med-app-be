package com.dc.medapp.medappbe.patient;

import com.dc.medapp.medappbe.appointments.AppointmentService;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.appointments.dto.AppointmentResponse;
import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.delegations.ReportDelegationService;
import com.dc.medapp.medappbe.patient.dto.PatientDataExportResponse;
import com.dc.medapp.medappbe.patient.dto.PatientProfileResponse;
import com.dc.medapp.medappbe.reports.MedicalReportRepository;
import com.dc.medapp.medappbe.reports.dto.MedicalReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatientService {

    public static final String ANONYMIZED_FIRST_NAME = "Paziente";
    public static final String ANONYMIZED_LAST_NAME = "Anonimizzato";

    private final PatientRepository patientRepository;
    private final AppointmentService appointmentService;
    private final MedicalReportRepository reportRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReportDelegationService reportDelegationService;

    /**
     * Right to erasure (GDPR art. 17) without breaking the duty to keep clinical and accounting records: the rows stay,
     * the identifying data goes. The account is switched off, the visits still to come are cancelled.
     *
     * @param patId
     */
    @Transactional
    @Audited(action = "ANONYMIZE_PATIENT", entity = "Patient", idArg = 0)
    public void anonymize(Long patId){
        var patient = findOrThrow(patId);
        if(patient.isAnonymized()) {
            throw new InvalidStateTransitionException("Il paziente è già stato anonimizzato.");
        }

        cancelFutureAppointments(patient);
        reportDelegationService.revokeAll(patId);

        patient.setFirstName(ANONYMIZED_FIRST_NAME);
        patient.setLastName(ANONYMIZED_LAST_NAME);
        patient.setBirthDate(null);
        patient.setAnonymized(true);

        var user = patient.getUser();
        user.setEmail("anonymized-"+patId+"@studio-medico.invalid");
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(false);
    }

    @Transactional(readOnly = true)
    @Audited(action = "EXPORT_PATIENT_DATA", entity = "Patient", idArg = 0)
    public PatientDataExportResponse exportData(Long patId){
        var patient = findOrThrow(patId);

        var appointments = appointmentService.findByPatient(patId).stream().map(app -> AppointmentResponse.fromEntity(app, false)).toList();
        var reports = reportRepository.findByAppointmentPatientIdOrderByCompletedAtDesc(patId)
                .stream().map(MedicalReportResponse::fromEntity).toList();
        return new PatientDataExportResponse(PatientProfileResponse.fromEntity(patient), appointments, reports);
    }

    private void cancelFutureAppointments(PatientEntity patient) {
        var now = LocalDateTime.now();
        appointmentService.findByPatient(patient.getId()).stream()
                .filter(app -> app.getStatus() == AppointmentStatus.BOOKED && app.getSlot().getStartTime().isAfter(now))
                .toList()
                .forEach(app -> appointmentService.cancelAppointment(app.getId(), patient.getId()));
    }

    private PatientEntity findOrThrow(Long id) {
        return patientRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Paziente " + id + " non trovato"));
    }
}
