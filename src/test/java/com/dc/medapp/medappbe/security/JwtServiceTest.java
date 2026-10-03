package com.dc.medapp.medappbe.security;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class JwtServiceTest {

    private JwtService jwtService;
    private User user;

    @BeforeEach
    public void setUp() {
        jwtService = new JwtService("test-secret-key-for-testing-purposes-only-32chars", 3600000); // 1 ora exp
        user = User.builder()
                .email("testuser@example.com")
                .role(Role.PAZIENTE)
                .passwordHash("hashedpassword")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    public void generateToken_extractEmail_returnsCorrectEmail() {
        String token = jwtService.generateToken(user);
        String extractedEmail = jwtService.extractEmail(token);
        assertThat(extractedEmail).isEqualTo(user.getEmail());
    }

    @Test
    void isTokenValid_returnsTrue_ForMatchingUserAndToken(){
        String token = jwtService.generateToken(user);
        boolean isValid = jwtService.isTokenValid(token, user);
        assertThat(isValid).isTrue();
    }

    @Test
    void isTokenValid_returnsFalse_ForNonMatchingUserAndToken(){
        String token = jwtService.generateToken(user);
        User anotherUser = User.builder()
                .email("anotheruser@example.com")
                .role(Role.PAZIENTE)
                .passwordHash("anotherhashedpassword")
                .build();
        boolean isValid = jwtService.isTokenValid(token, anotherUser);
        assertThat(isValid).isFalse();
    }
}