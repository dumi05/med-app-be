package com.dc.medapp.medappbe.config;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A unit test with mocks, not a Spring test: the seeded admin is shared by every test of the application (one
 * in-memory database), and whoever changes its password would change it for the others.
 */
public class DataSeederTest {

    private final SpecializationRepository specializations = mock(SpecializationRepository.class);
    private final PriceListRepository prices = mock(PriceListRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DataSeeder seeder = new DataSeeder(specializations, prices, users, new BCryptPasswordEncoder());

    private void seed() throws Exception {
        when(specializations.count()).thenReturn(1L);
        when(prices.count()).thenReturn(1L);
        ReflectionTestUtils.setField(seeder, "adminPassword", "admin123");
        seeder.run();
    }

    @Test
    void theSeededAdminMustChangeThePasswordAtTheFirstLogin() throws Exception {
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());

        seed();

        var saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@studio-medico.it");
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
    }

    @Test
    void anExistingAdminIsLeftAlone() throws Exception {
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.of(UserEntity.builder().email("admin@studio-medico.it").build()));

        seed();

        verify(users, never()).save(any());
    }
}