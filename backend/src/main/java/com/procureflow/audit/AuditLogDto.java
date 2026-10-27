package com.procureflow.audit;

import java.time.Instant;

public record AuditLogDto(Long id, Long actorId, String actorEmail, String action, String entityType,
                          Long entityId, String details, Instant createdAt) {

    public static AuditLogDto from(AuditLog a) {
        return new AuditLogDto(a.getId(), a.getActorId(), a.getActorEmail(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getDetails(), a.getCreatedAt());
    }
}
