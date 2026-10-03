package com.dc.medapp.medappbe.auth;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testFindByEmail() {
        // Given
        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("testpassword");
        user.setRole(Role.PAZIENTE);
        userRepository.save(user);

        // When
        Optional<User> foundUser = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getRole()).isEqualTo(Role.PAZIENTE);
    }

    @Test
    public void testExistsByEmail() {
        // When
        boolean userExists = userRepository.existsByEmail("nonexistent@example.com");

        // Then
        assertThat(userExists).isFalse();
    }
}
