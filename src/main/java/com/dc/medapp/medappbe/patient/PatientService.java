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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatientService {

    public static final String ANONYMIZED_FIRST_NAME = "Paziente";
    public static final String ANONYMIZED_LAST_NAME = "Anonimizzato";

    private static final int MIN_SEARCH_LENGTH = 3;
    private static final int MAX_SEARCH_RESULTS = 10;
    private static final int SEARCH_CANDIDATES = 200;

    private final PatientRepository patientRepository;
    private final AppointmentService appointmentService;
    private final MedicalReportRepository reportRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReportDelegationService reportDelegationService;

    @Transactional(readOnly = true)
    @Audited(action = "SEARCH_PATIENTS", entity = "Patient")
    public List<PatientEntity> search(String query){
        var words = Arrays.stream(query == null ? new String[0] : query.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(word -> !word.isEmpty())
                .toList();

        if(words.isEmpty() || String.join("", words).length() < MIN_SEARCH_LENGTH){
            throw new IllegalArgumentException("Scrivi almen    o " + MIN_SEARCH_LENGTH + " caratteri per cercare un paziente.");
        }
        return patientRepository.searchByText(escapeLike(words.getFirst()), PageRequest.of(0, SEARCH_CANDIDATES)).stream()
                .filter(patient -> {
                    var haystack = (patient.getFirstName() + " " + patient.getLastName() + " " + patient.getUser().getEmail()).toLowerCase(Locale.ROOT);
                    return words.stream().skip(1).allMatch(haystack::contains);
                })
                .limit(MAX_SEARCH_RESULTS)
                .toList();
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

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

    @Transactional(readOnly = true)
    public List<PatientEntity> findAllForAdmin(){
        return patientRepository.findByAnonymizedFalseOrderByLastNameAscFirstNameAsc();
    }

    @Transactional
    @Audited(action = "FORCE_PASSWORD_CHANGE", entity = "Patient", idArg = 0)
    public PatientEntity forcePasswordChange(Long patId) {
        var patient = findOrThrow(patId);
        if(patient.isAnonymized()){
            throw new ResourceNotFoundException("Paziente " + patId + " non trovato.");
        }
        patient.getUser().requirePasswordChange();
        return patient;
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
