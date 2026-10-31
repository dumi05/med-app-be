package com.dc.medapp.medappbe.security;

import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.common.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PasswordChangeRequiredFilter  extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of("/api/v1/auth/change-password", "/api/v1/auth/me");

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication != null && authentication.getPrincipal() instanceof UserEntity user
            && user.isMustChangePassword() && !ALLOWED_PATHS.contains(request.getRequestURI())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            objectMapper.writeValue(response.getOutputStream(), new ErrorResponse("PASSWORD_CHANGE_REQUIRED", "Devi cambiare la password prima di continuare.", Instant.now()));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
