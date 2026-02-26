package com.yoursp.uaepass.modules.compliance;

import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.face.FaceVerified;
import com.yoursp.uaepass.repository.*;
import com.yoursp.uaepass.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * PDPL Compliance endpoints for data deletion and export.
 */
@Slf4j
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserDataController {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final FaceVerificationRepository faceVerificationRepository;
    private final SigningJobRepository signingJobRepository;
    private final AuditService auditService;

    // ================================================================
    // DELETE /users/me/data — Requires face verification + confirmation
    // ================================================================

    @FaceVerified
    @DeleteMapping("/data")
    public ResponseEntity<?> deleteMyData(@RequestBody Map<String, String> body,
            HttpServletRequest request) {
        User user = getCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Require confirmation body
        String confirm = body.get("confirm");
        if (!"DELETE_MY_DATA".equals(confirm)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "CONFIRMATION_REQUIRED",
                    "message", "Include {\"confirm\": \"DELETE_MY_DATA\"} in request body"));
        }

        UUID userId = user.getId();
        Map<String, Object> deletionReport = new LinkedHashMap<>();

        // Delete user sessions
        int sessionCount = sessionRepository.deleteAllByUserId(userId);
        deletionReport.put("sessionsDeleted", sessionCount);

        // Delete face verifications
        int faceCount = faceVerificationRepository.deleteAllByUserId(userId);
        deletionReport.put("faceVerificationsDeleted", faceCount);

        // Delete signing jobs (records only — S3 PDFs have separate retention policy)
        int jobCount = signingJobRepository.deleteAllByUserId(userId);
        deletionReport.put("signingJobsDeleted", jobCount);

        // Anonymize user record (keep id for audit trail integrity)
        user.setFullNameEn(null);
        user.setFullNameAr(null);
        user.setFirstNameEn(null);
        user.setLastNameEn(null);
        user.setEmail(null);
        user.setMobile(null);
        user.setIdn(null);
        user.setNationalityEn(null);
        user.setGender(null);
        user.setUaepassUuid(null);
        user.setSpuuid(null);
        user.setIdType(null);
        user.setAcr(null);
        userRepository.save(user);
        deletionReport.put("userAnonymized", true);

        // Audit log — preserved (legal compliance requirement)
        deletionReport.put("auditLogsPreserved", true);
        deletionReport.put("reason", "Audit log entries are preserved per legal/compliance requirements");

        // Log the deletion request itself
        auditService.log(userId, "USER_DATA_DELETION_REQUEST", "User",
                userId.toString(), getClientIp(request),
                Map.of("deletionReport", deletionReport));

        log.info("User data deletion completed for userId={}", userId);

        return ResponseEntity.ok(Map.of(
                "status", "DELETED",
                "deletionReport", deletionReport,
                "timestamp", OffsetDateTime.now().toString()));
    }

    // ================================================================
    // GET /users/me/data-export
    // ================================================================

    @GetMapping("/data-export")
    public ResponseEntity<?> exportMyData(HttpServletRequest request) {
        User user = getCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = user.getId();

        // Profile data (no decrypted idn)
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", userId);
        profile.put("fullNameEn", user.getFullNameEn());
        profile.put("fullNameAr", user.getFullNameAr());
        profile.put("email", user.getEmail());
        profile.put("mobile", user.getMobile());
        profile.put("userType", user.getUserType());
        profile.put("nationality", user.getNationalityEn());
        profile.put("linkedAt", user.getLinkedAt());
        // idn is NOT included — stored encrypted, never decrypt for export

        // Signing history (metadata only, no PDF content)
        List<Map<String, Object>> signingHistory = signingJobRepository.findAllByUserId(userId)
                .stream()
                .map(job -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("jobId", job.getId());
                    entry.put("status", job.getStatus());
                    entry.put("createdAt", job.getCreatedAt());
                    entry.put("ltvApplied", job.getLtvApplied());
                    return entry;
                })
                .toList();

        // Face verification history
        List<Map<String, Object>> faceHistory = faceVerificationRepository.findAllByUserId(userId)
                .stream()
                .map(fv -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("verificationId", fv.getId());
                    entry.put("status", fv.getStatus());
                    entry.put("purpose", fv.getPurpose());
                    entry.put("createdAt", fv.getCreatedAt());
                    entry.put("verifiedAt", fv.getVerifiedAt());
                    return entry;
                })
                .toList();

        Map<String, Object> export = Map.of(
                "profile", profile,
                "signingHistory", signingHistory,
                "faceVerificationHistory", faceHistory,
                "exportedAt", OffsetDateTime.now().toString(),
                "note", "Raw signed PDFs and decrypted Emirates IDs are NOT included per policy");

        return ResponseEntity.ok(export);
    }

    private User getCurrentUser(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
