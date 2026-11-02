package com.dc.medapp.medappbe.specialization;

import com.dc.medapp.medappbe.specialization.dto.CreateSpecializationRequest;
import com.dc.medapp.medappbe.specialization.dto.SpecializationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/specializations")
@RequiredArgsConstructor
public class SpecializationController {

    private final SpecializationService service;

    @GetMapping
    public ResponseEntity<List<SpecializationResponse>> getAll(){
        return ResponseEntity.ok(service.getAll().stream().map(SpecializationResponse::fromEntity).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpecializationResponse> create(@Valid @RequestBody CreateSpecializationRequest request) {
        var specialization = service.create(request);
        return ResponseEntity.ok(SpecializationResponse.fromEntity(specialization));
    }
}
