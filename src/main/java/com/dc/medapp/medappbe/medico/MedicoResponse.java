package com.dc.medapp.medappbe.medico;

public record MedicoResponse(Long id, String nome, String cognome, String specializzazione) {
    public static MedicoResponse fromEntity(MedicoEntity medico) {
        return new MedicoResponse(
                medico.getId(),
                medico.getNome(),
                medico.getCognome(),
                medico.getSpecializzazione().getNome()
        );
    }
}
