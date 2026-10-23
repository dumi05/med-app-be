package com.dc.medapp.medappbe.delegations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreateReportDelegationRequest(
        @NotBlank String delegateName,
        @NotBlank @Email String delegateEmail,
        LocalDate expiresAt
) {
}
