package com.dc.medapp.medappbe.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testFindByEmailIgnoreCase() {
        // Given
        UserEntity user = new UserEntity();
        user.setEmail("test@example.com");
        user.setPasswordHash("testpassword");
        user.setRole(Role.PATIENT);
        userRepository.save(user);

        // When
        Optional<UserEntity> foundUser = userRepository.findByEmailIgnoreCase("test@example.com");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getRole()).isEqualTo(Role.PATIENT);
    }

    @Test
    public void testExistsByEmailIgnoreCase() {
        // When
        boolean userExists = userRepository.existsByEmailIgnoreCase("nonexistent@example.com");

        // Then
        assertThat(userExists).isFalse();
    }
}
