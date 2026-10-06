package com.dc.medapp.medappbe.agenda;

import com.dc.medapp.medappbe.agenda.dto.SlotRequest;
import com.dc.medapp.medappbe.agenda.dto.SlotResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("api/v1/agenda")
@RequiredArgsConstructor
public class AgendaController {
    private final AgendaService agendaService;

    @PostMapping("/slot")
    @PreAuthorize(("hasAnyRole('ADMIN', 'MEDICO')"))
    public ResponseEntity<SlotResponse> creaSlot(@Valid @RequestBody SlotRequest slotRequest) {
        return ResponseEntity.ok(SlotResponse.fromEntity(agendaService.createSlotDisponibilita(slotRequest.medicoId(), slotRequest.dataOra(), slotRequest.durataMinuti())));
    }

    @GetMapping("/disponibilita")
    public ResponseEntity<List<SlotResponse>> cercaDisponibilita(@RequestParam Long specId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok(agendaService.cercaDisponibilita(specId, data).stream().map(SlotResponse::fromEntity).toList());
    }
}
