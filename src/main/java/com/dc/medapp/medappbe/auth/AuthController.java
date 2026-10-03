package com.dc.medapp.medappbe.auth;

import com.dc.medapp.medappbe.auth.dto.AuthResponse;
import com.dc.medapp.medappbe.auth.dto.LoginRequest;
import com.dc.medapp.medappbe.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public AuthResponse me(@AuthenticationPrincipal User user) {
        return new AuthResponse(null, user.getEmail(), user.getRole());
    }
}
