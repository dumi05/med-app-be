package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.patient.PatientRepository;
import com.dc.medapp.medappbe.reports.MedicalReportEntity;
import com.dc.medapp.medappbe.reports.MedicalReportRepository;
import com.dc.medapp.medappbe.reports.MedicalReportService;
import com.dc.medapp.medappbe.reports.dto.AttachmentDownload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportDelegationService {
    private final ReportDelegationRepository reportDelegationRepository;
    private final PatientRepository patientRepository;
    private final MedicalReportRepository medicalReportRepository;
    private final MedicalReportService medicalReportService;

    @Transactional
    @Audited(action = "CREATE_REPORT_DELEGATION", entity = "ReportDelegation")
    public ReportDelegationEntity create(Long patientId, String delegateName, String delegateEmail, LocalDate expiresAt, boolean includedAttachments) {
        var patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paziente non trovato: " + patientId));

        if(expiresAt != null && expiresAt.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La data di scadenza non può essere nel passato.");
        }

        return reportDelegationRepository.save(
                ReportDelegationEntity.builder()
                        .delegateEmail(delegateEmail)
                        .delegateName(delegateName)
                        .expiresAt(expiresAt)
                        .patient(patient)
                        .includeAttachments(includedAttachments)
                        .token(UUID.randomUUID().toString())
                        .revoked(false)
                        .build()
        );
    }

    @Transactional(readOnly = true)
    public List<ReportDelegationEntity> findByPatient(Long patientId) {
        return reportDelegationRepository.findByPatientIdOrderByIdDesc(patientId);
    }

    @Transactional
    @Audited(action = "REVOKE_REPORT_DELEGATION", entity = "ReportDelegation", idArg = 0)
    public ReportDelegationEntity revoke(Long delegationId, Long patientId) {
        var delegation = reportDelegationRepository.findById(delegationId)
                .orElseThrow(() -> new ResourceNotFoundException("Delega non trovata: " + delegationId));
        if(!delegation.getPatient().getId().equals(patientId)) {
            throw new UnauthorizedAccessException("La delega appartiene ad un altro paziente.");
        }
        delegation.setRevoked(true);
        return delegation;
    }

    @Transactional
    public void revokeAll(Long patientId) {
        reportDelegationRepository.findByPatientIdOrderByIdDesc(patientId).forEach(pat -> pat.setRevoked(true));
    }

    @Transactional(readOnly = true)
    @Audited(action = "ACCESS_REPORTS_VIA_DELEGATION", entity = "ReportDelegation")
    public ReportDelegationEntity openByToken(String token) {
        return reportDelegationRepository.findByToken(token)
                .filter(del -> del.isActive(LocalDate.now()))
                .orElseThrow(() -> new ResourceNotFoundException("Link non valido o non più attivo."));
    }

    @Transactional(readOnly = true)
    public List<MedicalReportEntity> reportsOf(ReportDelegationEntity delegation) {
        return medicalReportRepository.findByAppointmentPatientIdOrderByCompletedAtDesc(delegation.getPatient().getId());
    }

    @Transactional(readOnly = true)
    @Audited(action = "ACCESS_ATTACHMENT_VIA_DELEGATION", entity = "ReportDelegation")
    public ReportDelegationEntity openForAttachments(String token){
        return reportDelegationRepository.findByToken(token)
                .filter(d -> d.isActive(LocalDate.now()) && d.isIncludeAttachments())
                .orElseThrow(() -> new ResourceNotFoundException("Link non valido o non più attivo."));
    }

    @Transactional(readOnly = true)
    public AttachmentDownload attachmentOf(ReportDelegationEntity delegation, Long reportId) {
        var report = medicalReportRepository.findById(reportId)
                .filter(found -> found.getAppointment().getPatient().getId().equals(delegation.getPatient().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Allegato non trovato."));
        return medicalReportService.downloadOf(report);
    }
}
