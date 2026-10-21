package com.dc.medapp.medappbe.security;

import com.dc.medapp.medappbe.auth.UserEntity;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    public static final String SYSTEM = "system";

    private CurrentUser(){}

    /**
     * Retrieve email from Security context of the user that acting currently on the tool.
     * @return String
     */
    public static String emailOrSystem() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication != null && authentication.getPrincipal() instanceof UserEntity user) {
            return user.getEmail();
        }
        return SYSTEM;
    }
}
