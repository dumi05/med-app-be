package com.dc.medapp.medappbe.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
    public static final int MAX_LIMIT = 200;

    @Transactional(readOnly = true)
    public List<AuditLogEntity> findLatest(int limit) {
        var size = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return auditLogRepository.findAll(PageRequest.of(0, size, Sort.Direction.DESC, "occurredAt", "id")).getContent();
    }
}
