package com.dc.medapp.medappbe.billing;

import com.dc.medapp.medappbe.appointments.AppointmentEntity;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BillingService {

    public static final String STANDARD_VISIT_TYPE = "Visita specialistica";

    private final InvoiceItemRepository invoiceItemRepository;
    private final PriceListRepository priceListRepository;

    @Transactional
    public InvoiceItemEntity issueInvoiceItem(AppointmentEntity appointment) {
        if(appointment.getStatus() != AppointmentStatus.REPORTED) {
            throw new InvalidStateTransitionException("Impossibile fatturare un appuntamento in stato " + appointment.getStatus());
        }

        var specializationId = appointment.getSlot().getDoctor().getSpecialization().getId();
        var price = priceListRepository.findBySpecializationIdAndVisitType(specializationId, STANDARD_VISIT_TYPE)
                .orElseThrow(() -> new ResourceNotFoundException("Nessun prezzo di listino per la specializzazione: " + specializationId));
        var item = invoiceItemRepository.save(InvoiceItemEntity.builder()
                                                .appointment(appointment)
                                                .amount(price.getPrice())
                                                .issuedAt(Instant.now())
                                                .build());

        appointment.setStatus(AppointmentStatus.INVOICED);
        appointment.setInvoicedAt(Instant.now());
        return item;
    }
}
