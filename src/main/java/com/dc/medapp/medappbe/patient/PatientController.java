package com.dc.medapp.medappbe.patient;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.patient.dto.PatientDataExportResponse;
import com.dc.medapp.medappbe.security.ProfileResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;
    private final ProfileResolver profileResolver;

    @GetMapping("/me/export")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientDataExportResponse> exportMyData(@AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(patientService.exportData(profileResolver.patientIdOf(user)));
    }

    @PostMapping("/me/anonymize")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Void> anonymizeMe(@AuthenticationPrincipal UserEntity user) {
        patientService.anonymize(profileResolver.patientIdOf(user));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/anonymize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> anonymize(@PathVariable Long id) {
        patientService.anonymize(id);
        return ResponseEntity.noContent().build();
    }
}
