package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.schedule.dto.SlotRequest;
import com.dc.medapp.medappbe.schedule.dto.SlotResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/schedule")
@RequiredArgsConstructor
public class ScheduleController {
    private final ScheduleService scheduleService;

    @PostMapping("/slot")
    @PreAuthorize(("hasAnyRole('ADMIN', 'DOCTOR')"))
    public ResponseEntity<SlotResponse> createSlot(@AuthenticationPrincipal UserEntity user, @Valid @RequestBody SlotRequest slotRequest) {
        return ResponseEntity.ok(SlotResponse.fromEntity(scheduleService.createAvailabilitySlotAs(user, slotRequest.doctorId(), slotRequest.startTime(), slotRequest.durationMinutes())));
    }

    @GetMapping("/availability")
    public ResponseEntity<List<SlotResponse>> searchAvailability(@RequestParam Long specializationId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(scheduleService.searchAvailability(specializationId, date).stream().map(SlotResponse::fromEntity).toList());
    }
}
