package com.dc.medapp.medappbe.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SlotDisponibilitaRepository extends JpaRepository<SlotDisponibilitaEntity, Long> {
    @Query("SELECT s FROM SlotDisponibilitaEntity s WHERE s.medico.specializzazione.id = :specId AND s.occupato = false and s.dataOra between :start and :end order by s.dataOra")
    List<SlotDisponibilitaEntity> findAvailableSlotsBySpecializzazioneIdAndDateRange(Long specId, LocalDateTime start, LocalDateTime end);
}
