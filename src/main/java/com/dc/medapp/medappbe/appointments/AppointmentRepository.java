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

    boolean existsBySlotDoctorIdAndStatusAndSlotStartTimeAfter(Long doctorId, AppointmentStatus status, LocalDateTime time);
    List<AppointmentEntity> findByPatientIdAndStatus(Long patientId, AppointmentStatus status);

    @Query("SELECT a from AppointmentEntity a WHERE a.status = com.dc.medapp.medappbe.appointments.AppointmentStatus.BOOKED " +
            "AND a.slot.doctor.id = :docId and a.slot.startTime >= :from AND a.slot.startTime < :to ORDER BY a.slot.startTime")
    List<AppointmentEntity> findBookedOfDoctorBetween(Long docId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT a FROM AppointmentEntity a WHERE a.status = com.dc.medapp.medappbe.appointments.AppointmentStatus.INVOICED " +
            "AND EXISTS (SELECT r.id FROM MedicalReportEntity r where r.appointment = a)")
    List<AppointmentEntity> findInvoicedWithReport();
}
