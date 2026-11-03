package com.dc.medapp.medappbe.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;

@Repository
public interface WorkingHoursRepository extends JpaRepository<WorkingHoursEntity, Long> {
    List<WorkingHoursEntity> findByDoctorId(Long docId);

    default List<WorkingHoursEntity> findSortedByDoctorId(Long doctorId) {
        return findByDoctorId(doctorId).stream()
                .sorted(Comparator.comparing(WorkingHoursEntity::getDayOfWeek).thenComparing(WorkingHoursEntity::getStartTime))
                .toList();
    }

    void deleteByDoctorId(Long doctorId);

    @Query("SELECT DISTINCT w.doctor.id FROM WorkingHoursEntity w")
    List<Long> findDoctorIdsWithWorkingHours();
}
