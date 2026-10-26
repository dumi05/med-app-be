package com.dc.medapp.medappbe.schedule;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.schedule.dto.BulkSlotRequest;
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

    @PostMapping("/slots/bulk")
    @PreAuthorize(("hasAnyRole('ADMIN', 'DOCTOR')"))
    public ResponseEntity<List<SlotResponse>> createSlots(@AuthenticationPrincipal UserEntity user, @Valid @RequestBody BulkSlotRequest slotRequest) {
        return ResponseEntity.ok(
                scheduleService.createAvailabilitySlotsAs(user, slotRequest.doctorId(), slotRequest.date(), slotRequest.startTime(), slotRequest.endTime(), slotRequest.slotMinutes()).stream()
                        .map(SlotResponse::fromEntity).toList());
    }

    @GetMapping("/availability")
    public ResponseEntity<List<SlotResponse>> searchAvailability(@RequestParam Long specializationId,
                                                                 @RequestParam(required = false) Long doctorId,
                                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(scheduleService.searchAvailability(specializationId, doctorId, from, to).stream().map(SlotResponse::fromEntity).toList());
    }

    @GetMapping("/slots/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<SlotResponse>> mySlots(@AuthenticationPrincipal UserEntity user,
                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(
                scheduleService.findMySlots(user, from, to).stream().map(SlotResponse::fromEntity).toList()
        );
    }
}
