package com.dc.medapp.medappbe.billing;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.delegations.DoctorDelegationService;
import com.dc.medapp.medappbe.reports.dto.AttachmentDownload;
import com.dc.medapp.medappbe.security.ProfileResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final AppointmentRepository appointmentRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final BillingService billingService;
    private final DoctorDelegationService delegationService;
    private final ProfileResolver profileResolver;
    private final InvoicePdfGenerator pdfGenerator;

    @Transactional
    public InvoiceItemEntity issue(UserEntity docUser, Long appointmentId){
        var appointment = findAppointment(appointmentId);
        if(!isAssignedDoctor(appointment, docUser)){
            throw new UnauthorizedAccessException("Solo il medico dell'appuntamento può fatturare.");
        }

        return billingService.issueInvoiceItem(appointment);
    }

    @Transactional(readOnly = true)
    @Audited(action = "READ_INVOICE", entity = "InvoiceItem", idArg = 1)
    public InvoiceItemEntity find(UserEntity actor, Long appointmentId){
        return readable(actor, appointmentId);
    }

    @Transactional(readOnly = true)
    @Audited(action = "READ_INVOICE", entity = "InvoiceItem", idArg = 1)
    public AttachmentDownload pdf(UserEntity user, Long appointmentId){
        var invoice = readable(user, appointmentId);
        var bytes = pdfGenerator.generate(invoice);
        return new AttachmentDownload(new ByteArrayResource(bytes), "application/pdf", (long) bytes.length, "fattura-"+invoice.getNumber()+".pdf");
    }

    private InvoiceItemEntity readable(UserEntity actor, Long appointmentId){
        var appointment = findAppointment(appointmentId);
        var isOwnerPatient = actor.getRole() == Role.PATIENT && appointment.getPatient().getUser().getId().equals(actor.getId());
        if(actor.getRole() != Role.ADMIN && !isOwnerPatient && !isAssignedDoctor(appointment, actor)){
            throw new UnauthorizedAccessException("Non hai accesso a questa fattura.");
        }
        if(appointment.getStatus() != AppointmentStatus.INVOICED && appointment.getStatus() != AppointmentStatus.REPORTED){
            throw new ResourceNotFoundException("Nessuna fattura per questo appuntamento.");
        }
        return invoiceItemRepository.findByAppointmentId(appointmentId).orElseThrow(() -> new ResourceNotFoundException("Nessuna fattura per questo appuntamento."));
    }

    private boolean isAssignedDoctor(AppointmentEntity appointment, UserEntity user){
        if(user.getRole() != Role.DOCTOR){
            return false;
        }
        var assigned = appointment.getSlot().getDoctor();
        return assigned.getUser().getId().equals(user.getId()) || delegationService.isAuthorized(profileResolver.doctorIdOf(user), assigned.getId());
    }

    private AppointmentEntity findAppointment(Long appointmentId){
        return appointmentRepository.findById(appointmentId).orElseThrow(() -> new ResourceNotFoundException("Appuntamento non trovato."));
    }
}
