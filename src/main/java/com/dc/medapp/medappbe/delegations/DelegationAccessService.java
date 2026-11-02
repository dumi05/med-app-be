package com.dc.medapp.medappbe.delegations;

import com.dc.medapp.medappbe.audit.Audited;
import com.dc.medapp.medappbe.common.exception.AccessCodeLockedException;
import com.dc.medapp.medappbe.common.exception.AccessCodeRequiredException;
import com.dc.medapp.medappbe.common.exception.InvalidAccessCodeException;
import com.dc.medapp.medappbe.common.exception.ResourceNotFoundException;
import com.dc.medapp.medappbe.delegations.dto.AccessGrant;
import com.dc.medapp.medappbe.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class DelegationAccessService {

    private static final String LINK_NOT_VALID = "Link non valido o non più attivo.";

    private final ReportDelegationRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TransactionTemplate ownTransaction;
    private final int maxFailures;
    private final Duration lockDuration;
    private final Duration accessDuration;

    public DelegationAccessService(ReportDelegationRepository repository,
                                   PasswordEncoder passwordEncoder,
                                   JwtService jwtService,
                                   PlatformTransactionManager transactionManager,
                                   @Value("${app.delegation.max-code-failures:5}") int maxFailures,
                                   @Value("${app.delegation.lock-minutes:15}") int lockMinutes,
                                   @Value("${app.delegation.access-minutes:15}") int accessMinutes) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.maxFailures = maxFailures;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
        this.accessDuration = Duration.ofMinutes(accessMinutes);
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public Long activeDelegationIdOf(String token) {
        return activeByToken(token).getId();
    }

    @Transactional
    @Audited(action = "UNLOCK_REPORT_DELEGATION", entity = "ReportDelegation", idArg = 0)
    public AccessGrant unlock(Long delegationId, String typedCode) {
        var delegation = repository.findById(delegationId)
                .filter(found -> found.isActive(LocalDate.now()))
                .orElseThrow(() -> new ResourceNotFoundException(LINK_NOT_VALID));

        var now = Instant.now();
        if (delegation.getLockedUntil() != null && delegation.getLockedUntil().isAfter(now)) {
            throw new AccessCodeLockedException(Duration.between(now, delegation.getLockedUntil()));
        }

        if (delegation.getAccessCodeHash() == null || !matches(typedCode, delegation.getAccessCodeHash())) {
            recordFailure(delegationId);
            throw new InvalidAccessCodeException();
        }

        delegation.setFailedCodeAttempts(0);
        delegation.setLockedUntil(null);
        return new AccessGrant(jwtService.generateDelegationAccessToken(delegation.getId(), accessDuration), accessDuration.toSeconds());
    }

    @Transactional(readOnly = true)
    public void requireAccess(String token, String accessToken) {
        var delegation = activeByToken(token);
        if (accessToken == null || accessToken.isBlank()) {
            throw new AccessCodeRequiredException();
        }
        try {
            if (!jwtService.delegationIdOf(accessToken).equals(delegation.getId())) {
                throw new AccessCodeRequiredException();
            }
        } catch (JwtException | IllegalArgumentException e) {
            throw new AccessCodeRequiredException();
        }
    }

    private ReportDelegationEntity activeByToken(String token) {
        return repository.findByToken(token)
                .filter(found -> found.isActive(LocalDate.now()))
                .orElseThrow(() -> new ResourceNotFoundException(LINK_NOT_VALID));
    }

    private boolean matches(String typedCode, String hash) {
        var code = AccessCode.normalize(typedCode);
        return code.length() == AccessCode.LENGTH && passwordEncoder.matches(code, hash);
    }

    private void recordFailure(Long delegationId) {
        try {
            ownTransaction.executeWithoutResult(status -> registerFailure(delegationId));
        } catch (OptimisticLockingFailureException e) {
            ownTransaction.executeWithoutResult(status -> registerFailure(delegationId));
        }
    }

    private void registerFailure(Long delegationId) {
        repository.findById(delegationId).ifPresent(delegation -> {
            delegation.setFailedCodeAttempts(delegation.getFailedCodeAttempts() + 1);
            if (delegation.getFailedCodeAttempts() >= maxFailures) {
                delegation.setLockedUntil(Instant.now().plus(lockDuration));
                delegation.setFailedCodeAttempts(0);
            }
            repository.saveAndFlush(delegation);
        });
    }
}