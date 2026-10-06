package com.dc.medapp.medappbe.agenda;

import com.dc.medapp.medappbe.common.exception.RisorsaNonTrovataException;
import com.dc.medapp.medappbe.medico.MedicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgendaService {
    private final SlotDisponibilitaRepository slotDisponibilitaRepository;
    private final MedicoRepository medicoRepository;

    public SlotDisponibilitaEntity createSlotDisponibilita(Long medicoId, LocalDateTime dataOra, int durataMinuti) {
        var medico = medicoRepository.findById(medicoId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Medico non trovato"));
        var slot = SlotDisponibilitaEntity.builder()
                .dataOra(dataOra)
                .durataMinuti(durataMinuti)
                .medico(medico)
                .occupato(false)
                .build();
        return slotDisponibilitaRepository.save(slot);
    }

    public List<SlotDisponibilitaEntity> cercaDisponibilita(Long specId, LocalDate data) {
        LocalDateTime dataStart = data.atStartOfDay();
        LocalDateTime dataEnd = data.atTime(LocalTime.MAX);
        return slotDisponibilitaRepository.findAvailableSlotsBySpecializzazioneIdAndDateRange(specId, dataStart, dataEnd);
    }
}
