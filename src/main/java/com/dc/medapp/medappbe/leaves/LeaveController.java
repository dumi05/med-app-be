package com.dc.medapp.medappbe.leaves;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.leaves.dto.CreateLeaveRequest;
import com.dc.medapp.medappbe.leaves.dto.LeavePreviewResponse;
import com.dc.medapp.medappbe.leaves.dto.LeaveResponse;
import com.dc.medapp.medappbe.leaves.dto.LeaveResultResponse;
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
@RequestMapping("/api/v1/doctor-leaves")
@PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService service;

    @GetMapping("/preview")
    public ResponseEntity<LeavePreviewResponse> preview(@AuthenticationPrincipal UserEntity user,
                                                        @RequestParam(required = false) Long doctorId,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)LocalDate startDate,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)LocalDate endDate,
                                                        @RequestParam(required = false) Long replacementDoctorId
                                                        ){
        return ResponseEntity.ok(service.preview(user, doctorId, startDate, endDate, replacementDoctorId));
    }

    @PostMapping
    public ResponseEntity<LeaveResultResponse> create(@AuthenticationPrincipal UserEntity user,
                                                      @Valid @RequestBody CreateLeaveRequest req){
        var result = service.create(user, req.doctorId(), req.startDate(), req.endDate(), req.replacementDoctorId());
        return ResponseEntity.ok(new LeaveResultResponse(result.leaveId(), result.reassigned(), result.cancelled()));
    }

    @GetMapping
    public ResponseEntity<List<LeaveResponse>> upcoming(@AuthenticationPrincipal UserEntity user,
                                                        @RequestParam(required = false) Long doctorId){
        return ResponseEntity.ok(service.find(user, doctorId).stream().map(LeaveResponse::fromEntity).toList());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserEntity user, @PathVariable Long id){
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
