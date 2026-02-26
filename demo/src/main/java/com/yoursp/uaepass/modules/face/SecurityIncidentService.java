package com.yoursp.uaepass.modules.face;

import com.yoursp.uaepass.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Handles security incidents — especially UUID mismatch during face
 * verification.
 * <p>
 * In staging: logs to console. In production: hook to email/Slack alert.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityIncidentService {

    private final AuditService auditService;

    /**
     * Log a security incident to the audit log and trigger an alert.
     *
     * @param type    incident type (e.g. "FACE_UUID_MISMATCH")
     * @param userId  the user involved
     * @param details context including expected UUID, received UUID, IP, purpose
     */
    public void logSecurityIncident(String type, UUID userId, Map<String, Object> details) {
        log.error("🚨 SECURITY INCIDENT [{}] userId={}, details={}", type, userId, details);

        auditService.log(userId, "SECURITY_INCIDENT", "FaceVerification",
                type, null, details);

        // STUB: In production, hook to email/Slack/PagerDuty alert
        // alertService.sendUrgentAlert("Security Incident: " + type, details);
        log.warn("ALERT STUB: Would send notification for incident type={}, userId={}", type, userId);
    }
}
