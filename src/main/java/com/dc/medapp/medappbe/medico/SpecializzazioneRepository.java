package com.dc.medapp.medappbe.medico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpecializzazioneRepository extends JpaRepository<SpecializzazioneEntity, Long> {
    Optional<SpecializzazioneEntity> findByNome(String nome);
}
