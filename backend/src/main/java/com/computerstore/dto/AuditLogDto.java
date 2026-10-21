package com.computerstore.dto;

import java.time.Instant;

public record AuditLogDto(
        Long id,
        Long userId,
        String userName,
        String action,
        String entityName,
        String entityId,
        String oldValues,
        String newValues,
        String ipAddress,
        Instant createdAt) {}
