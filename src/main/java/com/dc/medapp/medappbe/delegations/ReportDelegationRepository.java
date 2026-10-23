package com.dc.medapp.medappbe.delegations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportDelegationRepository extends JpaRepository<ReportDelegationEntity, Long> {
    Optional<ReportDelegationEntity> findByToken(String token);
    List<ReportDelegationEntity> findByPatientIdOrderByIdDesc(Long patientId);
}
