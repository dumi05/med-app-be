package com.dc.medapp.medappbe.reports;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.common.exception.FileTooLargeException;
import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.delegations.DoctorDelegationService;
import com.dc.medapp.medappbe.reports.dto.AttachmentDownload;
import com.dc.medapp.medappbe.security.ProfileResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MedicalReportService {

    public static final long MAX_ATTACHMENT_SIZE = 10L * 1024 * 1024;

    private final MedicalReportRepository medicalReportRepository;
    private final AppointmentRepository appointmentRepository;
    private final ProfileResolver profileResolver;
    private final DoctorDelegationService delegationService;
    private final BillingService billingService;
    private final AttachmentStorage attachmentStorage;

    @Transactional
    @Audited(action = "WRITE_REPORT", entity = "MedicalReport")
    public MedicalReportEntity createReport(UserEntity user, Long appointmentId, String diagnosis, MultipartFile file) {
        var appointment = findAppointmentOrThrow(appointmentId);

        if(!isAssignedDoctor(appointment, user)){
            throw new UnauthorizedAccessException("Solo il medico dell'appuntamento può scrivere il referto.");
        }

        if(appointment.getStatus() != AppointmentStatus.COMPLETED){
            throw new InvalidStateTransitionException("Impossibile redigere il report in stato " + appointment.getStatus());
        }

        var type = file == null ? null : checkFile(file);
        var storedName = file == null ? null : store(file);

        try{
            var report = MedicalReportEntity.builder()
                    .appointment(appointment)
                    .attachmentStoredName(storedName)
                    .attachmentContentType(type == null ? null : type.getContentType())
                    .attachmentSize(file == null ? null : file.getSize())
                    .diagnosisText(diagnosis)
                    .completedAt(Instant.now())
                    .build();

            var result = medicalReportRepository.save(report);

            appointment.setStatus(AppointmentStatus.REPORTED);
            appointment.setReportedAt(Instant.now());

            billingService.issueInvoiceItem(appointment);

            return result;
        } catch (RuntimeException e) {
            if(storedName != null) {
                attachmentStorage.delete(storedName);
            }
            throw e;
        }

    }

    @Transactional(readOnly = true)
    @Audited(action = "READ_ATTACHMENT", entity = "MedicalReport", idArg = 1)
    public AttachmentDownload getAttachment(UserEntity user, Long reportId) {
        var report = medicalReportRepository.findById(reportId).orElseThrow(() -> new ResourceNotFoundException("Report non trovato: " + reportId));
        checkReadAccess(report.getAppointment(), user);
        return downloadOf(report);
    }

    public AttachmentDownload downloadOf(MedicalReportEntity report) {
        if(!report.hasAttachment()) {
            throw new ResourceNotFoundException("Il referto non ha un allegato.");
        }
        var type = AttachmentType.fromContentType(report.getAttachmentContentType()).orElseThrow(() -> new IllegalStateException("Tipo allegato non riconosciuto: " + report.getId()));
        return new AttachmentDownload(attachmentStorage.open(report.getAttachmentStoredName()), type.getContentType(), report.getAttachmentSize(), "referto-"+report.getId()+"."+type.getExtension());
    }

    private AttachmentType checkFile(MultipartFile file) {
        if(file.isEmpty()) throw new IllegalArgumentException("Il file è vuoto.");

        if(file.getSize() > MAX_ATTACHMENT_SIZE) throw new FileTooLargeException("Il file supera la dimensione massima di 10 MB.");

        byte[] head;
        try(var i = file.getInputStream()) {
            head = i.readNBytes(AttachmentType.HEAD_SIZE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the uploaded file.", e);
        }
        return AttachmentType.detect(head).orElseThrow(() -> new IllegalArgumentException("Sono ammessi solo file PDF, JPEG, PNG."));
    }

    private String store(MultipartFile file){
        String storedName;
        try(var i = file.getInputStream()) {
            storedName= attachmentStorage.save(i);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the uploaded file.", e);
        }
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if(status != STATUS_COMMITTED) {
                        attachmentStorage.delete(storedName);
                    }
                }
            });
        }
        return storedName;
    }

    @Transactional(readOnly = true)
    @Audited(action = "READ_REPORT", entity = "MedicalReport", idArg = 1)
    public MedicalReportEntity getReport(UserEntity user, Long reportId) {
        var report = medicalReportRepository.findById(reportId).orElseThrow(() -> new ResourceNotFoundException("Report non trovato: " + reportId));
        checkReadAccess(report.getAppointment(), user);
        return report;
    }

    @Transactional(readOnly = true)
    @Audited(action = "READ_REPORT", entity = "MedicalReport")
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
        if(user.getRole() != Role.DOCTOR){
            return false;
        }
        var assignedDoctor = appointment.getSlot().getDoctor();
        if(assignedDoctor.getUser().getId().equals(user.getId())){
            return true;
        }
        return delegationService.isAuthorized(profileResolver.doctorIdOf(user), assignedDoctor.getId());
    }

    private AppointmentEntity findAppointmentOrThrow(Long appointmentId) {
        return this.appointmentRepository.findById(appointmentId).orElseThrow(() -> new ResourceNotFoundException("Appuntamento non presente."));
    }
}
