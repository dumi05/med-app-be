package com.dc.medapp.medappbe.paziente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PazienteRepository extends JpaRepository<PazienteEntity, Long> {
    Optional<PazienteEntity> findByUserId(Long userId);
}
