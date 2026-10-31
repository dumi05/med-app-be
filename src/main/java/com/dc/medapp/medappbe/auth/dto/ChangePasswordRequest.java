package com.dc.medapp.medappbe.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(@NotBlank @Size(max = 256) String currentPassword,
                                    @NotBlank @Size(max = 256) String newPassword) {
}
