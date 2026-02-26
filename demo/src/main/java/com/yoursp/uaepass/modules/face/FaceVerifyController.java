package com.yoursp.uaepass.modules.face;

import com.yoursp.uaepass.model.entity.FaceVerification;
import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.auth.StateService;
import com.yoursp.uaepass.modules.auth.dto.StatePayload;
import com.yoursp.uaepass.modules.face.dto.FaceVerifyInitiateRequest;
import com.yoursp.uaepass.modules.face.dto.FaceVerifyInitiateResponse;
import com.yoursp.uaepass.modules.face.dto.FaceVerifyStatusResponse;
import com.yoursp.uaepass.repository.FaceVerificationRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Face verification controller for biometric transaction confirmation.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 * <li>POST /face/verify/initiate — Start face verification</li>
 * <li>GET /face/verify/callback — OAuth callback after face scan</li>
 * <li>GET /face/verify/status/{id} — Poll verification status</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/face/verify")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class FaceVerifyController {

    private final FaceVerificationService faceService;
    private final FaceVerificationRepository faceRepo;
    private final StateService stateService;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${uaepass.client-id:}")
    private String clientId;

    @Value("${uaepass.client-secret:}")
    private String clientSecret;

    @Value("${uaepass.base-url:https://stg-id.uaepass.ae}")
    private String uaepassBaseUrl;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    // ================================================================
    // POST /face/verify/initiate
    // ================================================================

    @PostMapping("/initiate")
    public ResponseEntity<?> initiate(@Valid @RequestBody FaceVerifyInitiateRequest request,
            HttpServletRequest httpRequest) {
        User user = getCurrentUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Must be linked to UAE PASS
        if (user.getUaepassUuid() == null || user.getUaepassUuid().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "NOT_LINKED",
                            "message", "User must be linked to UAE PASS for face verification"));
        }

        try {
            FaceVerifyInitiateResponse response = faceService.initiate(user, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "VALIDATION_ERROR", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Face verify initiation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INITIATE_FAILED", "message", e.getMessage()));
        }
    }

    // ================================================================
    // GET /face/verify/callback
    // ================================================================

    @GetMapping("/callback")
    public void callback(@RequestParam(value = "code", required = false) String code,
            @RequestParam("state") String state,
            @RequestParam(value = "error", required = false) String error,
            HttpServletRequest httpRequest,
            HttpServletResponse response) throws Exception {

        // Consume state first to get verificationId
        StatePayload statePayload;
        try {
            statePayload = stateService.consumeState(state);
        } catch (Exception e) {
            log.error("Invalid/expired state in face verify callback: {}", e.getMessage());
            response.sendRedirect(frontendUrl + "/face/verify/result?error=invalid_state");
            return;
        }

        if (!"FACE_VERIFY".equals(statePayload.flowType())) {
            response.sendRedirect(frontendUrl + "/face/verify/result?error=invalid_flow");
            return;
        }

        // Extract verificationId from redirectAfter field (overloaded)
        String verificationIdStr = statePayload.redirectAfter();
        UUID userId = statePayload.userId();

        if (verificationIdStr == null || userId == null) {
            response.sendRedirect(frontendUrl + "/face/verify/result?error=invalid_state");
            return;
        }

        UUID verificationId = UUID.fromString(verificationIdStr);

        // Handle error from UAE PASS
        if (error != null) {
            log.warn("Face verify callback error: {}, verificationId={}", error, verificationId);
            faceRepo.findById(verificationId).ifPresent(fv -> {
                fv.setStatus("FAILED");
                faceRepo.save(fv);
            });
            response.sendRedirect(frontendUrl + "/face/verify/result?error=" + error
                    + "&verificationId=" + verificationId);
            return;
        }

        if (code == null) {
            response.sendRedirect(frontendUrl + "/face/verify/result?error=no_code");
            return;
        }

        try {
            // Exchange code → access token → call userinfo
            String accessToken = exchangeCodeForToken(code);
            String returnedUuid = fetchUserUuid(accessToken);

            // CRITICAL: UUID match check
            boolean success = faceService.complete(
                    verificationId, userId, returnedUuid, httpRequest.getRemoteAddr());

            if (success) {
                response.sendRedirect(frontendUrl + "/face/verify/result?status=verified"
                        + "&verificationId=" + verificationId);
            } else {
                // Generic 403 — do NOT reveal UUID mismatch reason
                response.sendRedirect(frontendUrl + "/face/verify/result?error=verification_failed"
                        + "&verificationId=" + verificationId);
            }

        } catch (Exception e) {
            log.error("Face verify callback processing failed: {}", e.getMessage());
            faceRepo.findById(verificationId).ifPresent(fv -> {
                fv.setStatus("FAILED");
                faceRepo.save(fv);
            });
            response.sendRedirect(frontendUrl + "/face/verify/result?error=processing_failed");
        }
    }

    // ================================================================
    // GET /face/verify/status/{verificationId}
    // ================================================================

    @GetMapping("/status/{verificationId}")
    public ResponseEntity<?> status(@PathVariable UUID verificationId,
            HttpServletRequest httpRequest) {
        User user = getCurrentUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        FaceVerification fv = faceRepo.findById(verificationId).orElse(null);
        if (fv == null || !fv.getUserId().equals(user.getId())) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(FaceVerifyStatusResponse.builder()
                .verificationId(fv.getId())
                .status(fv.getStatus())
                .purpose(fv.getPurpose())
                .verifiedAt(fv.getVerifiedAt())
                .build());
    }

    // ================================================================
    // Helpers
    // ================================================================

    private User getCurrentUser(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    private String exchangeCodeForToken(String code) {
        try {
            String credentials = Base64.getEncoder().encodeToString(
                    (clientId + ":" + clientSecret).getBytes());

            String body = "grant_type=authorization_code&code=" + code
                    + "&redirect_uri=" + appBaseUrl + "/face/verify/callback";

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(uaepassBaseUrl + "/idshub/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "Basic " + credentials)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();

            java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Token exchange failed: HTTP " + response.statusCode());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(response.body(), Map.class);

            return (String) tokenResponse.get("access_token");

        } catch (Exception e) {
            throw new RuntimeException("Failed to exchange code for token", e);
        }
    }

    private String fetchUserUuid(String accessToken) {
        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(uaepassBaseUrl + "/idshub/userinfo"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Userinfo call failed: HTTP " + response.statusCode());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(response.body(), Map.class);

            return (String) userInfo.get("uuid");

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch user UUID from userinfo", e);
        }
    }
}
