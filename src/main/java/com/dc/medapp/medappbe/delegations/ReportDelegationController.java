package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.delegations.dto.CreateReportDelegationRequest;
import com.dc.medapp.medappbe.delegations.dto.ReportDelegationResponse;
import com.dc.medapp.medappbe.delegations.dto.SharedMedicalReportResponse;
import com.dc.medapp.medappbe.security.ProfileResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/report-delegations")
@RequiredArgsConstructor
public class ReportDelegationController {

    private final ReportDelegationService service;
    private final ProfileResolver profileResolver;

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ReportDelegationResponse> create(@AuthenticationPrincipal UserEntity user,
                                                           @Valid @RequestBody CreateReportDelegationRequest req) {
        return ResponseEntity.ok(ReportDelegationResponse.fromEntity(service.create(profileResolver.patientIdOf(user), req.delegateName(), req.delegateEmail(), req.expiresAt())));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<ReportDelegationResponse>> mine(@AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(
                service.findByPatient(profileResolver.patientIdOf(user))
                        .stream().map(ReportDelegationResponse::fromEntity).toList()
        );
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ReportDelegationResponse> revoke(@AuthenticationPrincipal UserEntity user, @PathVariable Long id) {
        return ResponseEntity.ok(
                ReportDelegationResponse.fromEntity(
                        service.revoke(id, profileResolver.patientIdOf(user)))
        );
    }

    @GetMapping("/{token}/medical-reports")
    public ResponseEntity<List<SharedMedicalReportResponse>> reportsByToken(@PathVariable String token) {
        var delegation = service.openByToken(token);
        return ResponseEntity.ok(
            service.reportsOf(delegation).stream().map(SharedMedicalReportResponse::fromEntity).toList()
        );
    }
}
