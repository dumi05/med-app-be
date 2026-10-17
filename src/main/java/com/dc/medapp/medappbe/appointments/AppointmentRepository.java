package com.dc.medapp.medappbe.appointments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<AppointmentEntity, Long> {
    @Query("SELECT a FROM AppointmentEntity a WHERE a.patient.id = :patientId AND a.slot.startTime < :now AND a.status <> com.dc.medapp.medappbe.appointments.AppointmentStatus.CANCELLED")
    List<AppointmentEntity> findPastNotCancelledByPatientId(Long patientId, LocalDateTime now);
    List<AppointmentEntity> findByPatientIdOrderBySlotStartTimeAsc(Long patientId);
    List<AppointmentEntity> findBySlotDoctorIdInOrderBySlotStartTimeAsc(List<Long> doctorIds);

    long countByStatus(AppointmentStatus status);
    long countByStatusAndSlotStartTimeBefore(AppointmentStatus status, LocalDateTime time);
    long countByStatusAndNoShowRiskTrueAndSlotStartTimeAfter(AppointmentStatus status, LocalDateTime time);
}
