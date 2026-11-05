package com.dc.medapp.medappbe.config;

import com.dc.medapp.medappbe.appointments.AppointmentRepository;
import com.dc.medapp.medappbe.appointments.AppointmentStatus;
import com.dc.medapp.medappbe.billing.BillingService;
import com.dc.medapp.medappbe.billing.InvoiceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class InvoiceOrderMigration implements CommandLineRunner {

    private final AppointmentRepository appointmentRepository;
    private final InvoiceItemRepository invoiceRepository;

    @Override
    @Transactional
    public void run(String... args) {
        var toReported = appointmentRepository.findInvoicedWithReport();
        toReported.forEach(app -> {
            app.setStatus(AppointmentStatus.REPORTED);
            if(app.getReportedAt() == null){
                app.setReportedAt(app.getInvoicedAt());
            }
        });
        var numbered = invoiceRepository.findByNumberIsNull();
        numbered.forEach(item -> {
            item.setNumber(BillingService.numberOf(item));
            if(item.getPaidAt() == null){
                item.setPaidAt(item.getIssuedAt());
            }
        });
        if(!toReported.isEmpty() || !numbered.isEmpty()){
            log.info("Invoice order migration: {} visits set to REPORTED, {} invoices numbered.", toReported.size(), numbered.size());
        }
    }
}
