package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.schedule.dto.UpdateWorkingHoursRequest;
import com.dc.medapp.medappbe.schedule.dto.WorkingHoursBand;
import com.dc.medapp.medappbe.schedule.dto.WorkingHoursResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule/working-hours")
@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
@RequiredArgsConstructor
public class WorkingHoursController {

    private final WorkingHoursService workingHoursService;

    @GetMapping
    public ResponseEntity<WorkingHoursResponse> get(@AuthenticationPrincipal UserEntity user, @RequestParam(required = false) Long doctorId) {
        var bands = workingHoursService.find(user, doctorId).stream().map(WorkingHoursBand::fromEntity).toList();
        return ResponseEntity.ok(new WorkingHoursResponse(doctorId, bands, 0, 0));
    }

    @PutMapping
    public ResponseEntity<WorkingHoursResponse> replace(@AuthenticationPrincipal UserEntity user, @Valid @RequestBody UpdateWorkingHoursRequest request) {
        return ResponseEntity.ok(workingHoursService.replace(user, request.doctorId(), request.bands()));
    }
}