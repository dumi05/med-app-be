package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.auth.Role;
import com.dc.medapp.medappbe.auth.UserEntity;
import com.dc.medapp.medappbe.auth.UserRepository;
import com.dc.medapp.medappbe.common.exception.InvalidStateTransitionException;
import com.dc.medapp.medappbe.common.exception.UnauthorizedAccessException;
import com.dc.medapp.medappbe.doctor.DoctorEntity;
import com.dc.medapp.medappbe.doctor.DoctorRepository;
import com.dc.medapp.medappbe.specialization.SpecializationEntity;
import com.dc.medapp.medappbe.specialization.SpecializationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class DoctorDelegationServiceTest {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DoctorDelegationService delegationService;
    @Autowired
    private DoctorDelegationRepository delegationRepository;
    @Autowired
    private SpecializationRepository specializationRepository;
    @Autowired
    private DoctorRepository doctorRepository;

    private SpecializationEntity specialization;
    private DoctorEntity delegating;
    private DoctorEntity delegate;

    @BeforeEach
    void setUp() {
        specialization = specializationRepository.save(SpecializationEntity.builder().name("Cardiologia-deleg-test").build());
        delegating = createDoctor("delegating.doc@test.it", true);
        delegate = createDoctor("delegate.doc@test.com", true);
    }

    // propose for delegation
    @Test
    void propose_createsAProposedDelegation(){
        var delegation = delegationService.propose(delegating.getId(), delegate.getId(), today(), today().plusDays(7));

        assertThat(delegation.getId()).isNotNull();
        assertThat(delegation.getStatus()).isEqualTo(DelegationStatus.PROPOSED);
        assertThat(delegation.getDelegatingDoctor().getId()).isEqualTo(delegating.getId());
        assertThat(delegation.getDelegateDoctor().getId()).isEqualTo(delegate.getId());
    }

    @Test
    void propose_throwsIllegalArgumentException_whenTheDelegateHasNoConsent(){
        var noConsent = createDoctor("no.del@test.ok", false);

        assertThrows(IllegalArgumentException.class, () -> delegationService.propose(delegating.getId(), noConsent.getId(), today(), today().plusDays(7)));
    }

    @Test
    void propose_throwsIllegalArgumentException_whenTheDelegatingDoctorHasNoConsent() {
        var noConsent = createDoctor("no.consnet@lok.is", false);
        assertThrows(IllegalArgumentException.class, () -> delegationService.propose(noConsent.getId(), delegate.getId(), today(), today().plusDays(7)));
    }

    @Test
    void propose_throwsIllegalArgumentException_whenADoctorDelegateHimself() {
        assertThrows(IllegalArgumentException.class, () -> delegationService.propose(delegate.getId(), delegate.getId(), today(), today().plusDays(7)));
    }

    @Test
    void propose_throwsIllegalArgumentException_whenTheEndIsBeforeTheStart() {
        assertThrows(IllegalArgumentException.class, () -> delegationService.propose(delegating.getId(), delegate.getId(), today().plusDays(7), today()));
    }

    // accept / reject Delegation
    @Test
    void accept_byTheDelegate_makesTheDelegationActive(){
        var delegation = delegationService.propose(delegating.getId(), delegate.getId(), today(), today().plusDays(7));
        var accepted = delegationService.accept(delegation.getId(), delegate.getId());

        assertThat(accepted.getStatus()).isEqualTo(DelegationStatus.ACCEPTED);
        assertThat(delegationService.isAuthorized(delegate.getId(), delegating.getId())).isTrue();
    }

    @Test
    void accept_throwsUnauthorizedAccessException_whenTheActorIsNotTheDelegate(){
        var delegation = delegationService.propose(delegating.getId(), delegate.getId(), today(), today().plusDays(7));
        var third = createDoctor("third.doc@test.ok", true);

        assertThrows(UnauthorizedAccessException.class, () -> delegationService.accept(delegation.getId(), third.getId()));
        assertThrows(UnauthorizedAccessException.class, () -> delegationService.accept(delegation.getId(), delegating.getId()));
    }

    @Test
    void accept_throwsInvalidStateTransitionException_whenAlreadyAccepted() {
        var delegation = delegationService.propose(delegating.getId(), delegate.getId(), today(), today().plusDays(7));
        delegationService.accept(delegation.getId(), delegate.getId());

        assertThrows(InvalidStateTransitionException.class, () -> delegationService.accept(delegation.getId(), delegate.getId()));
    }

    @Test
    void reject_byTheDelegate_setsRejected_andAuthorizesNobody(){
        var delegation = delegationService.propose(delegating.getId(), delegate.getId(), today(), today().plusDays(7));
        var rejected = delegationService.reject(delegation.getId(), delegate.getId());

        assertThat(rejected.getStatus()).isEqualTo(DelegationStatus.REJECTED);
        assertThat(delegationService.isAuthorized(delegate.getId(), delegating.getId())).isFalse();
    }

    // is Authorized / active delegating doctors ids
    @Test
    void isAuthorized_isTrue_forTheAssignedDoctorHimself() {
        assertThat(delegationService.isAuthorized(delegating.getId(), delegating.getId())).isTrue();
    }

    @Test
    void isAuthorized_isFalse_withoutDelegation(){
        assertThat(delegationService.isAuthorized(delegate.getId(), delegating.getId())).isFalse();
    }

    @Test
    void isAuthorized_isFalse_whenTheDelegationIsOnlyProposed() {
        delegationService.propose(delegating.getId(), delegate.getId(), today(), today().plusDays(7));
        assertThat(delegationService.isAuthorized(delegate.getId(), delegating.getId())).isFalse();
    }

    @Test
    void isAuthorized_isFalse_whenTheAcceptedDelegationIsOutsideItsDates() {
        saveAccepted(today().minusDays(10), today().minusDays(1));
        assertThat(delegationService.isAuthorized(delegate.getId(), delegating.getId())).isFalse();

        saveAccepted(today().plusDays(1), today().plusDays(10));
        assertThat(delegationService.isAuthorized(delegate.getId(), delegating.getId())).isFalse();
    }

    @Test
    void isAuthorized_isDirectional(){
        saveAccepted(today().minusDays(1), today().plusDays(1));
        assertThat(delegationService.isAuthorized(delegate.getId(), delegating.getId())).isTrue();
        assertThat(delegationService.isAuthorized(delegating.getId(), delegate.getId())).isFalse();
    }

    @Test
    void activeDelegatingDoctorIds_returnsTheDoctorsWhoDelegatedToME(){
        saveAccepted(today().minusDays(1), today().plusDays(1));

        assertThat(delegationService.activeDelegatingDoctorIds(delegate.getId())).containsExactly(delegating.getId());
        assertThat(delegationService.activeDelegatingDoctorIds(delegating.getId())).isEmpty();
    }

    // some helpers for those tests
    private LocalDate today(){
        return LocalDate.now();
    }

    private void saveAccepted(LocalDate start, LocalDate end){
        delegationRepository.save(
                DoctorDelegationEntity.builder()
                        .delegatingDoctor(delegating)
                        .delegateDoctor(delegate)
                        .startDate(start)
                        .endDate(end)
                        .status(DelegationStatus.ACCEPTED)
                        .build());
    }

    private DoctorEntity createDoctor(String mail, boolean consent) {
        var user = userRepository.save(UserEntity.builder().email(mail).passwordHash("PASSWORD").role(Role.DOCTOR).build());
        return doctorRepository.save(
                DoctorEntity.builder()
                        .specialization(specialization)
                        .user(user)
                        .firstName("Antonio")
                        .lastName("Nonni")
                        .delegationConsent(consent)
                        .build());
    }
}
