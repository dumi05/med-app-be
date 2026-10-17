package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.delegations.dto.CreateDoctorDelegationRequest;
import com.dc.medapp.medappbe.delegations.dto.DoctorDelegationResponse;
import com.dc.medapp.medappbe.security.ProfileResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/doctor-delegations")
@PreAuthorize("hasRole('DOCTOR')")
@RequiredArgsConstructor
public class DoctorDelegationController {

    private final DoctorDelegationService service;
    private final ProfileResolver profileResolver;

    @PostMapping
    public ResponseEntity<DoctorDelegationResponse> propose(@AuthenticationPrincipal UserEntity user,
                                                            @Valid @RequestBody CreateDoctorDelegationRequest request) {
        var del = service.propose(profileResolver.doctorIdOf(user), request.delegateDoctorId(), request.startDate(), request.endDate());
        return ResponseEntity.ok(DoctorDelegationResponse.fromEntity(del));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<DoctorDelegationResponse> accept(@AuthenticationPrincipal UserEntity user,
                                                                   @PathVariable Long id) {
        return ResponseEntity.ok(DoctorDelegationResponse.fromEntity(service.accept(id, profileResolver.doctorIdOf(user))));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<DoctorDelegationResponse> reject(@AuthenticationPrincipal UserEntity user,
                                                                   @PathVariable Long id) {
        return ResponseEntity.ok(DoctorDelegationResponse.fromEntity(service.reject(id, profileResolver.doctorIdOf(user))));
    }

    @GetMapping("/me")
    public ResponseEntity<List<DoctorDelegationResponse>> mine(@AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(service.findInvolvingDoctors(profileResolver.doctorIdOf(user))
                .stream().map(DoctorDelegationResponse::fromEntity).toList()
        );
    }

}
