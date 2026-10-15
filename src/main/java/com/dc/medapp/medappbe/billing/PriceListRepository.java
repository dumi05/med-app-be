package com.dc.medapp.medappbe.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PriceListRepository extends JpaRepository<PriceListEntity, Long> {
    Optional<PriceListEntity> findBySpecializationIdAndVisitType(Long specializationId, String visitType);
}
