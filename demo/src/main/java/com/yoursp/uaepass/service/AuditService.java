package com.yoursp.uaepass.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursp.uaepass.model.entity.AuditLog;
import com.yoursp.uaepass.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Service for recording audit trail entries.
 * Should be called from every sensitive operation (login, sign, verify, etc.).
 */
@SuppressWarnings("null")
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Record an audit log entry.
     *
     * @param userId     the user performing the action (nullable for system
     *                   actions)
     * @param action     short action descriptor, e.g. "LOGIN", "SIGN_DOCUMENT"
     * @param entityType the type of entity affected, e.g. "SigningJob", "User"
     * @param entityId   the ID of the affected entity
     * @param ip         client IP address
     * @param metadata   arbitrary key-value metadata (serialized as JSONB)
     */
    public void log(UUID userId, String action, String entityType, String entityId,
            String ip, Map<String, Object> metadata) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .ipAddress(ip)
                    .metadata(metadata != null ? objectMapper.writeValueAsString(metadata) : null)
                    .build();

            auditLogRepository.save(entry);
            log.debug("Audit logged: action={}, entity={}:{}, user={}", action, entityType, entityId, userId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit metadata for action={}: {}", action, e.getMessage());
            // Still save without metadata rather than losing the audit entry
            AuditLog entry = AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .ipAddress(ip)
                    .build();
            auditLogRepository.save(entry);
        }
    }
}
