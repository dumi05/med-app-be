package com.dc.medapp.medappbe.medico;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/medici")
@RequiredArgsConstructor
public class MedicoController {
    private final MedicoService medicoService;

    @GetMapping
    public ResponseEntity<List<MedicoResponse>> getAllMedico(){
        return ResponseEntity.ok(medicoService.findAllMedico().stream().map(MedicoResponse::fromEntity).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MedicoAdminResponse> creaMedico(@Valid @RequestBody CreaMedicoRequest request) {
        var medico = medicoService.creaMedico(request.email(), request.password(), request.nome(), request.cognome(), request.specializzazioneId(), request.consensoDelega());
        return ResponseEntity.ok(MedicoAdminResponse.fromEntity(medico));
    }
}
