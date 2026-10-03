package com.dc.medapp.medappbe.auth.dto;

import com.dc.medapp.medappbe.auth.Role;

public record AuthResponse(String token, String email, Role role) {
}
