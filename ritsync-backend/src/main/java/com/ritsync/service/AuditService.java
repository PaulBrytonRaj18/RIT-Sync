package com.ritsync.service;

import com.ritsync.entity.AuditLog;
import com.ritsync.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(Long actorId, String actorEmail, String action,
                    String targetTable, Long targetId,
                    Map<String, Object> payload, String ipAddress) {
        var log = AuditLog.builder()
            .actorId(actorId)
            .actorEmail(actorEmail)
            .action(action)
            .targetTable(targetTable)
            .targetId(targetId)
            .payload(payload)
            .ipAddress(ipAddress)
            .build();
        auditLogRepository.save(log);
    }
}
