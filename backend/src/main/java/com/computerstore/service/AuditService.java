package com.computerstore.service;

import com.computerstore.entity.AuditLog;
import com.computerstore.repository.AuditLogRepository;
import com.computerstore.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ghi nhật ký thao tác (FR-06).
 * Lưu ý: KHÔNG đưa mật khẩu/token vào oldValues/newValues.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final ObjectMapper mapper;

    /** Tạo Map giữ thứ tự, cho phép giá trị null: fields("name", x, "role", y). */
    public static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return m;
    }

    @Transactional
    public void log(String action, String entityName, String entityId,
                    Object oldValues, Object newValues, Long userId) {
        Long actor = userId != null ? userId : currentUser.userId().orElse(null);

        AuditLog entry = new AuditLog();
        entry.setUser(actor == null ? null : users.getReferenceById(actor));
        entry.setAction(action);
        entry.setEntityName(entityName);
        entry.setEntityId(entityId);
        entry.setOldValues(toJson(oldValues));
        entry.setNewValues(toJson(newValues));
        entry.setIpAddress(currentUser.ip());
        repo.save(entry);
    }

    public void log(String action, String entityName, String entityId) {
        log(action, entityName, entityId, null, null, null);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Không serialize được dữ liệu audit", e);
            return null;
        }
    }
}
