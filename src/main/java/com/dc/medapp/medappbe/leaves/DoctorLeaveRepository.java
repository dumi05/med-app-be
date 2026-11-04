package com.dc.medapp.medappbe.leaves;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DoctorLeaveRepository extends JpaRepository<DoctorLeaveEntity, Long> {
    boolean existsByDoctorIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(Long doctorId, LocalDate startDate, LocalDate endDate);

    List<DoctorLeaveEntity> findByDoctorIdAndEndDateGreaterThanEqualOrderByStartDate(Long doctorId, LocalDate endDate);

    default boolean isAway(Long doctorId, LocalDate day) {
        return existsByDoctorIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(doctorId, day, day);
    }

    default boolean overlaps(Long doctorId, LocalDate start, LocalDate end){
        return existsByDoctorIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(doctorId, end, start);
    }
}
