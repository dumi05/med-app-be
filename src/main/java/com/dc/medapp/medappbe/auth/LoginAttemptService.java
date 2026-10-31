package com.dc.medapp.medappbe.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
public class LoginAttemptService {

    private final LoginAttemptRepository attemptRepo;
    private final TransactionTemplate ownTransaction;
    private final int maxFailures;
    private final Duration lockoutMinutes;

    public LoginAttemptService(LoginAttemptRepository attemptRepo,
                               PlatformTransactionManager transactionManager,
                               @Value("${app.security.max-failed-logins}") int maxFailures,
                               @Value("${app.security.lockout-minutes}") int lockoutMinutes) {
        this.attemptRepo = attemptRepo;
        this.maxFailures = maxFailures;
        this.lockoutMinutes = Duration.ofMinutes(lockoutMinutes);
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public Optional<Duration> remainingLock(String email){
        var now = Instant.now();
        return attemptRepo.findByEmail(email)
                .map(LoginAttemptEntity::getLockedUntil)
                .filter(until -> until.isAfter(now))
                .map(until -> Duration.between(now, until));
    }

    public void recordFailure(String email){
        try {
            ownTransaction.executeWithoutResult(status -> registerFailure(email));
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
            ownTransaction.executeWithoutResult(status -> registerFailure(email));
        }
    }

    @Transactional
    public void recordSuccess(String email){
        attemptRepo.deleteByEmail(email);
    }

    private void registerFailure(String email){
        var now = Instant.now();
        var attempt = attemptRepo.findByEmail(email).orElseGet(() -> LoginAttemptEntity.builder().email(email).failures(0).build());
        if(attempt.getLastFailureAt() != null && attempt.getLastFailureAt().isBefore(now.minus(lockoutMinutes))){
            attempt.setFailures(0);
        }
        attempt.setFailures(attempt.getFailures() + 1);
        attempt.setLastFailureAt(now);
        if(attempt.getFailures() >= maxFailures){
            attempt.setLockedUntil(now.plus(lockoutMinutes));
            attempt.setFailures(0);
            log.info("Too many failed logins for {}: locked until {}", email, attempt.getLockedUntil());
        }
        attemptRepo.saveAndFlush(attempt);
    }
}
