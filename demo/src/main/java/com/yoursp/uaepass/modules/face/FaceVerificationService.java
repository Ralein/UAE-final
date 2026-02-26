package com.yoursp.uaepass.modules.face;

import com.yoursp.uaepass.model.entity.FaceVerification;
import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.auth.StateService;
import com.yoursp.uaepass.modules.face.dto.FaceVerifyInitiateRequest;
import com.yoursp.uaepass.modules.face.dto.FaceVerifyInitiateResponse;
import com.yoursp.uaepass.repository.FaceVerificationRepository;
import com.yoursp.uaepass.repository.UserRepository;
import com.yoursp.uaepass.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Face verification service — handles initiate + complete flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class FaceVerificationService {

    private final FaceVerificationRepository faceRepo;
    private final UserRepository userRepository;
    private final StateService stateService;
    private final AuditService auditService;
    private final SecurityIncidentService securityIncidentService;

    @Value("${uaepass.base-url:https://stg-id.uaepass.ae}")
    private String uaepassBaseUrl;

    @Value("${uaepass.client-id:}")
    private String clientId;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Value("${face.acr-values:urn:digitalid:authentication:flow:mobileid}")
    private String faceAcrValues;

    @Value("${face.verification-window-minutes:15}")
    private int verificationWindowMinutes;

    // ================================================================
    // Initiate
    // ================================================================

    /**
     * Initiate face verification — resolves username, creates record, builds auth
     * URL.
     *
     * @param user    the current session user
     * @param request initiation params (purpose, transactionRef, usernameType)
     * @return response with verificationId and authorization URL
     */
    public FaceVerifyInitiateResponse initiate(User user, FaceVerifyInitiateRequest request) {
        // Resolve username based on type
        String usernameValue = resolveUsername(user, request.getUsernameType());

        // Create face_verifications record
        FaceVerification verification = FaceVerification.builder()
                .userId(user.getId())
                .purpose(request.getPurpose())
                .transactionRef(request.getTransactionRef())
                .status("PENDING")
                .usernameUsed(request.getUsernameType())
                .build();
        FaceVerification saved = faceRepo.save(verification);

        // Generate state — store verificationId in redirectAfter field (overloaded)
        String state = stateService.generateState(
                "FACE_VERIFY",
                saved.getId().toString(), // verificationId stored as redirectAfter
                user.getId());

        // Build authorization URL
        String authUrl = UriComponentsBuilder
                .fromUriString(uaepassBaseUrl + "/idshub/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", appBaseUrl + "/face/verify/callback")
                .queryParam("scope", "openid urn:uae:digitalid:profile:general")
                .queryParam("state", state)
                .queryParam("acr_values", faceAcrValues)
                .queryParam("username", usernameValue)
                .queryParam("ui_locales", "en")
                .build()
                .toUriString();

        log.info("Face verify initiated: verificationId={}, userId={}, usernameType={}",
                saved.getId(), user.getId(), request.getUsernameType());

        return new FaceVerifyInitiateResponse(saved.getId(), authUrl, 300);
    }

    // ================================================================
    // Complete (called from callback)
    // ================================================================

    /**
     * Complete face verification — compare returned UUID against session user's
     * UUID.
     *
     * @param verificationId the verification record ID
     * @param userId         the user from state
     * @param returnedUuid   the uuid from /idshub/userinfo response
     * @param remoteAddr     client IP for security logging
     * @return true if UUID matches, false if mismatch (security incident)
     */
    public boolean complete(UUID verificationId, UUID userId, String returnedUuid, String remoteAddr) {
        FaceVerification verification = faceRepo.findById(verificationId)
                .orElseThrow(() -> new RuntimeException("Face verification not found: " + verificationId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        String expectedUuid = user.getUaepassUuid();

        // CRITICAL: UUID match check
        boolean uuidMatch = expectedUuid != null && expectedUuid.equals(returnedUuid);

        verification.setVerifiedUuid(returnedUuid);
        verification.setUuidMatch(uuidMatch);

        if (uuidMatch) {
            verification.setStatus("VERIFIED");
            verification.setVerifiedAt(OffsetDateTime.now());
            faceRepo.save(verification);

            auditService.log(userId, "FACE_VERIFY_SUCCESS", "FaceVerification",
                    verificationId.toString(), null,
                    Map.of("purpose", verification.getPurpose(),
                            "transactionRef", verification.getTransactionRef()));

            log.info("Face verify SUCCESS: verificationId={}, userId={}", verificationId, userId);
            return true;

        } else {
            // SECURITY INCIDENT — UUID mismatch
            verification.setStatus("FAILED");
            faceRepo.save(verification);

            securityIncidentService.logSecurityIncident("FACE_UUID_MISMATCH", userId,
                    Map.of(
                            "expected_uuid", expectedUuid != null ? expectedUuid : "null",
                            "received_uuid", returnedUuid != null ? returnedUuid : "null",
                            "ip", remoteAddr,
                            "purpose", verification.getPurpose(),
                            "transactionRef", verification.getTransactionRef(),
                            "verificationId", verificationId.toString()));

            log.error("Face verify FAILED — UUID MISMATCH: verificationId={}, userId={}",
                    verificationId, userId);
            return false;
        }
    }

    // ================================================================
    // Check recent verification
    // ================================================================

    /**
     * Check if user has a recent verified face verification within the configured
     * window.
     */
    public boolean hasRecentVerification(UUID userId) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(verificationWindowMinutes);
        return faceRepo.findRecentVerified(userId, cutoff).isPresent();
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String resolveUsername(User user, String usernameType) {
        return switch (usernameType) {
            case "EID" -> {
                if ("SOP1".equalsIgnoreCase(user.getUserType())) {
                    throw new IllegalArgumentException(
                            "SOP1 visitors cannot use EID for face verification. Use MOBILE or EMAIL.");
                }
                String idn = user.getIdn();
                if (idn == null || idn.isBlank()) {
                    throw new IllegalArgumentException("No Emirates ID found for this user");
                }
                yield idn;
            }
            case "MOBILE" -> {
                String mobile = user.getMobile();
                if (mobile == null || mobile.isBlank()) {
                    throw new IllegalArgumentException("No mobile number found for this user");
                }
                yield mobile;
            }
            case "EMAIL" -> {
                String email = user.getEmail();
                if (email == null || email.isBlank()) {
                    throw new IllegalArgumentException("No email found for this user");
                }
                yield email;
            }
            default -> throw new IllegalArgumentException("Invalid usernameType: " + usernameType);
        };
    }
}
