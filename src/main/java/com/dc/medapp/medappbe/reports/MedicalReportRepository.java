package com.dc.medapp.medappbe.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MedicalReportRepository extends JpaRepository<MedicalReportEntity, Long> {
    Optional<MedicalReportEntity> findByAppointmentId(Long appointmentId);
}
