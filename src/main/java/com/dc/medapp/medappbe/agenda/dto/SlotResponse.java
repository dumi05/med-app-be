package com.dc.medapp.medappbe.agenda.dto;

import java.time.LocalDateTime;

public record SlotResponse(Long id, Long medicoId, String medicoNome, String medicoCognome, LocalDateTime dataOra, int durataMinuti, boolean occupato) {
    public static SlotResponse fromEntity(com.dc.medapp.medappbe.agenda.SlotDisponibilitaEntity entity) {
        return new SlotResponse(
                entity.getId(),
                entity.getMedico().getId(),
                entity.getMedico().getNome(),
                entity.getMedico().getCognome(),
                entity.getDataOra(),
                entity.getDurataMinuti(),
                entity.isOccupato()
        );
    }
}
