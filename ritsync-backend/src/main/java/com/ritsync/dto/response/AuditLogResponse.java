package com.ritsync.dto.response;

import com.ritsync.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.Map;

public record AuditLogResponse(
    Long id,
    Long actorId,
    String actorEmail,
    String action,
    String targetTable,
    Long targetId,
    Map<String, Object> payload,
    String ipAddress,
    LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
            log.getId(),
            log.getActorId(),
            log.getActorEmail(),
            log.getAction(),
            log.getTargetTable(),
            log.getTargetId(),
            log.getPayload(),
            log.getIpAddress(),
            log.getCreatedAt()
        );
    }
}
