package com.dc.medapp.medappbe.medico;

public record MedicoAdminResponse(Long id, String nome, String cognome, String email, String specializzazione, boolean consensoDelega) {
    public static MedicoAdminResponse fromEntity(MedicoEntity medico) {
        return new MedicoAdminResponse(
                medico.getId(),
                medico.getNome(),
                medico.getCognome(),
                medico.getUser().getEmail(),
                medico.getSpecializzazione().getNome(),
                medico.isConsensoDelega()
        );
    }
}
