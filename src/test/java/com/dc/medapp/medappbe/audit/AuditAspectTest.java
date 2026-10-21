package com.dc.medapp.medappbe.audit;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.billing.PriceListEntity;
import com.dc.medapp.medappbe.billing.PriceListRepository;
import com.dc.medapp.medappbe.billing.PriceListService;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.security.CurrentUser;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class AuditAspectTest {
    private static final String ADMIN_EMAIL = "admin@audit.com";

    @Autowired
    private PriceListService priceListService;
    @Autowired
    private PriceListRepository priceListRepository;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private PriceListEntity entry;

    @BeforeEach
    void setUp() {
        var specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia.test").build());
        entry = priceListRepository.save(PriceListEntity.builder().price(BigDecimal.ONE).visitType("Visita specialistica").specialization(specialization).build());
        var admin = userRepository.save(UserEntity.builder().passwordHash("hash").email(ADMIN_EMAIL).role(Role.ADMIN).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditedCall_thatSucceeds_isLoggedWithTheIdOfTheReturnedEntity() {
        priceListService.updatePrice(entry.getId(), new BigDecimal("120.00"));

        var lines = linesOf(ADMIN_EMAIL);
        assertThat(lines).hasSize(1);
        var line = lines.get(0);
        assertThat(line.getAction()).isEqualTo("UPDATE_PRICE");
        assertThat(line.getEntityName()).isEqualTo("PriceList");
        assertThat(line.getEntityId()).isEqualTo(entry.getId());
        assertThat(line.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(line.getFailureReason()).isNull();
        assertThat(line.getOccurredAt()).isNotNull();
    }

    @Test
    void auditedCall_thatFails_isLoggedWithTheReasonAndTheRequestedId_andTheExceptionIsNotNull(){
        assertThrows(ResourceNotFoundException.class, () -> priceListService.updatePrice(999_999L, new BigDecimal("10.00")));
        var lines = linesOf(ADMIN_EMAIL);
        assertThat(lines).hasSize(1);
        var line = lines.get(0);
        assertThat(line.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(line.getFailureReason()).isEqualTo("ResourceNotFoundException");
        assertThat(line.getEntityId()).isEqualTo(999_999L);
    }

    @Test
    void auditedCall_withoutAuthenticatedUser_isLoggedAsSystem() {
        SecurityContextHolder.clearContext();

        priceListService.updatePrice(entry.getId(), new BigDecimal("150.00"));

        assertThat(linesOf(CurrentUser.SYSTEM)).extracting(AuditLogEntity::getAction).contains("UPDATE_PRICE");
    }

    private List<AuditLogEntity> linesOf(String email) {
        return auditLogRepository.findAll().stream().filter(line -> line.getUserEmail().equals(email)).toList();
    }
}
