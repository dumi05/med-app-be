package com.dc.medapp.medappbe.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItemEntity, Long> {
    Optional<InvoiceItemEntity> findByAppointmentId(Long appointmentId);
}
