package com.dc.medapp.medappbe.doctor;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.doctor.dto.CreateDoctorRequest;
import com.dc.medapp.medappbe.doctor.dto.DoctorAdminResponse;
import com.dc.medapp.medappbe.doctor.dto.DoctorResponse;
import com.dc.medapp.medappbe.doctor.dto.UpdateDoctorRequest;
import com.dc.medapp.medappbe.security.ProfileResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
public class DoctorController {
    private final DoctorService doctorService;
    private final ProfileResolver profileResolver;

    @GetMapping
    public ResponseEntity<List<DoctorResponse>> getAllDoctors(){
        return ResponseEntity.ok(doctorService.findAllDoctors().stream().map(DoctorResponse::fromEntity).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DoctorAdminResponse> createDoctor(@Valid @RequestBody CreateDoctorRequest request) {
        var doctor = doctorService.createDoctor(request.email(), request.password(), request.firstName(), request.lastName(), request.specializationId(), request.delegationConsent());
        return ResponseEntity.ok(DoctorAdminResponse.fromEntity(doctor));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorResponse> me(@AuthenticationPrincipal UserEntity user){
        var doctorId = profileResolver.doctorIdOf(user);
        return ResponseEntity.ok(DoctorResponse.fromEntity(doctorService.getDoctorById(doctorId)));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DoctorAdminResponse>> getAllDoctorsAdmin(){
        return ResponseEntity.ok(doctorService.findAllDoctorsForAdmin().stream().map(DoctorAdminResponse::fromEntity).toList());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DoctorAdminResponse> updateDoctor(@PathVariable Long id, @Valid @RequestBody UpdateDoctorRequest request) {
        return ResponseEntity.ok(
                DoctorAdminResponse.fromEntity(doctorService.updateDoctor(id, request.firstName(), request.lastName(), request.specializationId(), request.delegationConsent())));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DoctorAdminResponse> deactivateDoctor(@PathVariable Long id){
        return ResponseEntity.ok(
                DoctorAdminResponse.fromEntity(doctorService.deactivate(id))
        );
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DoctorAdminResponse> activateDoctor(@PathVariable Long id){
        return ResponseEntity.ok(
                DoctorAdminResponse.fromEntity(doctorService.activate(id))
        );
    }
}
