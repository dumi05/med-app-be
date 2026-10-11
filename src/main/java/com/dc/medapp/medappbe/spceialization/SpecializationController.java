package com.dc.medapp.medappbe.spceialization;

import com.dc.medapp.medappbe.spceialization.dto.SpecializationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
