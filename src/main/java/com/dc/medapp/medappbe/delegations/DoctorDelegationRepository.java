package com.dc.medapp.medappbe.delegations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DoctorDelegationRepository extends JpaRepository<DoctorDelegationEntity, Long> {

    @Query("""
        SELECT COUNT(d) > 0 FROM DoctorDelegationEntity d 
        WHERE d.delegatingDoctor.id = :delegatingDoctorId
        AND d.delegateDoctor.id = :delegateDoctorId
        AND d.status = com.dc.medapp.medappbe.delegations.DelegationStatus.ACCEPTED
        AND :today between d.startDate and d.endDate
    """)
    boolean existsActiveDelegation(Long delegatingDoctorId, Long delegateDoctorId, LocalDate today);

    @Query("""
        SELECT d.delegatingDoctor.id FROM DoctorDelegationEntity d
        WHERE d.delegateDoctor.id = :delegateDoctorId
        AND d.status = com.dc.medapp.medappbe.delegations.DelegationStatus.ACCEPTED
        AND :today between d.startDate and d.endDate
    """)
    List<Long> findActiveDelegatingDoctorIds(Long delegateDoctorId, LocalDate today);

    @Query("""
        SELECT d FROM DoctorDelegationEntity d 
        where d.delegatingDoctor.id = :doctorId or d.delegateDoctor.id = :doctorId
        ORDER BY d.startDate DESC
    """)
    List<DoctorDelegationEntity> findInvolvingDoctors(Long doctorId);
}
