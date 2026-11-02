package com.dc.medapp.medappbe.delegations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnlockReportDelegationRequest(
        @NotBlank @Size(max = 32) String code
) {
}
