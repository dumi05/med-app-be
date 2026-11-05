package com.dc.medapp.medappbe.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItemEntity, Long> {
    Optional<InvoiceItemEntity> findByAppointmentId(Long appointmentId);

    @Query("""
        SELECT SUM(i.amount) FROM InvoiceItemEntity  i
    """)
    BigDecimal getTotalAmount();

    @Query("""
        SELECT s.name as specializationName, SUM(i.amount) as totalRevenue 
        FROM InvoiceItemEntity i 
        JOIN i.appointment a 
        JOIN a.slot sl
        JOIN sl.doctor d 
        JOIN d.specialization s 
        GROUP BY s.name
        ORDER BY s.name
    """)
    List<SpecializationRevenueView> sumAmountBySpecialization();

    List<InvoiceItemEntity> findByNumberIsNull();
}
