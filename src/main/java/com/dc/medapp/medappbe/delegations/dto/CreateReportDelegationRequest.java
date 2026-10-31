package com.dc.medapp.medappbe.delegations.dto;

import com.dc.medapp.medappbe.common.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreateReportDelegationRequest(
        @NotBlank String delegateName,
        @NotBlank @ValidEmail String delegateEmail,
        LocalDate expiresAt,
        Boolean includeAttachments
) {
}
