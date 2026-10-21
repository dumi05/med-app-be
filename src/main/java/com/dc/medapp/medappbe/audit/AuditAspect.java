package com.dc.medapp.medappbe.audit;

import com.dc.medapp.medappbe.common.BaseEntity;
import com.dc.medapp.medappbe.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {
    private final AuditLogRepository logRepository;

    @Around("@annotation(com.dc.medapp.medappbe.audit.Audited)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        var audited = auditedOf(joinPoint);
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            saveLog(audited, AuditOutcome.FAILURE, ex.getClass().getSimpleName(), argumentId(joinPoint, audited));
            throw ex;
        }
        saveLog(audited, AuditOutcome.SUCCESS, null, entityId(result, joinPoint, audited));
        return result;
    }

    private Audited auditedOf(ProceedingJoinPoint joinPoint){
        var method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        return AnnotationUtils.findAnnotation(method, Audited.class);
    }

    private Long entityId(Object res, ProceedingJoinPoint joinPoint, Audited audited){
        if(res instanceof BaseEntity entity){
            return entity.getId();
        }
        return argumentId(joinPoint, audited);
    }

    private Long argumentId(ProceedingJoinPoint joinPoint, Audited audited){
        var args = joinPoint.getArgs();
        if(audited.idArg() >= 0 && audited.idArg() < args.length && args[audited.idArg()] instanceof Long id){
            return id;
        }
        return null;
    }

    private void saveLog(Audited audited, AuditOutcome outcome, String failureReason, Long entityId){
        try{
            logRepository.save(
                    AuditLogEntity
                            .builder()
                            .userEmail(CurrentUser.emailOrSystem())
                            .action(audited.action())
                            .entityName(audited.entity())
                            .entityId(entityId)
                            .occurredAt(Instant.now())
                            .outcome(outcome)
                            .failureReason(failureReason)
                            .build()
            );
        } catch (RuntimeException ex){
            log.error("Could not write the audit log for{}", audited.action(), ex);
        }
    }
}
