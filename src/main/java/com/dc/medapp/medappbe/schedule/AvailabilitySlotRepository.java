package com.dc.medapp.medappbe.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlotEntity, Long> {
    @Query("SELECT s FROM AvailabilitySlotEntity s " +
            "WHERE s.doctor.specialization.id = :specializationId " +
            "AND s.booked = false " +
            "AND s.doctor.user.enabled = true " +
            "AND s.startTime between :start and :end " +
            "AND s.startTime > :now " +
            "order by s.startTime, s.id")
    List<AvailabilitySlotEntity> findAvailableSlotsBySpecializationIdAndDateRange(Long specializationId, LocalDateTime start, LocalDateTime end, LocalDateTime now);

    @Query("SELECT s FROM AvailabilitySlotEntity s " +
            "WHERE s.doctor.specialization.id = :specializationId " +
            "AND s.doctor.id = :doctorId " +
            "AND s.booked = false " +
            "AND s.doctor.user.enabled = true " +
            "AND s.startTime BETWEEN :start AND :end " +
            "AND s.startTime > :now " +
            "ORDER BY s.startTime, s.id")
    List<AvailabilitySlotEntity> findAvailableByDoctor(Long specializationId, Long doctorId, LocalDateTime start, LocalDateTime end, LocalDateTime now);

    List<AvailabilitySlotEntity> findByDoctorIdAndStartTimeBetweenOrderByStartTime(Long doctorId, LocalDateTime start, LocalDateTime end);
}
