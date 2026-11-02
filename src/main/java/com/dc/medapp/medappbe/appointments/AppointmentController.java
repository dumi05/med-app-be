package com.dc.medapp.medappbe.appointments;

import com.dc.medapp.medappbe.appointments.dto.AppointmentResponse;
import com.dc.medapp.medappbe.appointments.dto.CreateAppointmentRequest;
import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.security.ProfileResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final ProfileResolver profileResolver;

    @PostMapping
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<AppointmentResponse> create(@AuthenticationPrincipal UserEntity user, @Valid @RequestBody CreateAppointmentRequest request){
        var appointment = appointmentService.createAppointmentAs(user, request.slotId(), request.patientId());
        return ResponseEntity.ok(AppointmentResponse.fromEntity(appointment, false));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<AppointmentResponse> complete(@AuthenticationPrincipal UserEntity user, @PathVariable Long id){
        var appointment = appointmentService.completeVisit(id, profileResolver.doctorIdOf(user));
        return ResponseEntity.ok(AppointmentResponse.fromEntity(appointment, true));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> cancel(@AuthenticationPrincipal UserEntity user, @PathVariable Long id){
        var appointment = appointmentService.cancelAppointment(id, profileResolver.patientIdOf(user));
        return ResponseEntity.ok(AppointmentResponse.fromEntity(appointment, false));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public ResponseEntity<List<AppointmentResponse>> myAppointments(@AuthenticationPrincipal UserEntity user){
        boolean isPatient = user.getRole() == Role.PATIENT;
        var appointments = isPatient ?
                            appointmentService.findByPatient(profileResolver.patientIdOf(user)) :
                            appointmentService.findByDoctor(profileResolver.doctorIdOf(user));
        return ResponseEntity.ok(
                appointments.stream()
                        .map(app -> AppointmentResponse.fromEntity(app, !isPatient))
                        .toList()
        );
    }
}
