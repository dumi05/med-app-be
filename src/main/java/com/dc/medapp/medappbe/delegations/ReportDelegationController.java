package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.delegations.dto.*;
import com.dc.medapp.medappbe.reports.AttachmentHttp;
import com.dc.medapp.medappbe.security.ProfileResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
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
    private final DelegationAccessService accessService;
    private final ProfileResolver profileResolver;
    public static final String ACCESS_HEADER = "X-Delegation-Access";

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ReportDelegationResponse> create(@AuthenticationPrincipal UserEntity user,
                                                           @Valid @RequestBody CreateReportDelegationRequest req) {
        return ResponseEntity.ok(ReportDelegationResponse.created(service.create(profileResolver.patientIdOf(user), req.delegateName(), req.delegateEmail(), req.expiresAt(), Boolean.TRUE.equals(req.includeAttachments()))));
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

    @PostMapping("/{token}/access")
    public ResponseEntity<AccessGrantResponse> access(@PathVariable String token,
                                                      @Valid @RequestBody UnlockReportDelegationRequest req) {
        var grant = accessService.unlock(accessService.activeDelegationIdOf(token), req.code());
        return ResponseEntity.ok(AccessGrantResponse.fromGrant(grant));
    }

    @GetMapping("/{token}/medical-reports")
    public ResponseEntity<List<SharedMedicalReportResponse>> reportsByToken(@PathVariable String token,
                                                                            @RequestHeader(value = ACCESS_HEADER, required = false) String accessToken) {
        var delegation = service.openByToken(token, accessToken);
        return ResponseEntity.ok(
            service.reportsOf(delegation).stream().map(s -> SharedMedicalReportResponse.fromEntity(s, delegation.isIncludeAttachments())).toList()
        );
    }

    @GetMapping("/{token}/medical-reports/{reportId}/attachment")
    public ResponseEntity<Resource> attachmentByToken(@PathVariable String token, @PathVariable Long reportId, @RequestHeader(value = ACCESS_HEADER, required = false) String accessToken) {
        var delegation = service.openForAttachments(token, accessToken);
        return AttachmentHttp.asDownload(service.attachmentOf(delegation, reportId));
    }
}
