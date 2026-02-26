package com.yoursp.uaepass.modules.hashsigning;

import com.yoursp.uaepass.model.entity.SigningJob;
import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.auth.StateService;
import com.yoursp.uaepass.modules.auth.dto.StatePayload;
import com.yoursp.uaepass.modules.hashsigning.dto.*;
import com.yoursp.uaepass.modules.signature.dto.SigningJobStatusResponse;
import com.yoursp.uaepass.repository.SigningJobRepository;
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
 * Controller for UAE PASS Hash Signing operations.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 * <li>POST /hashsign/initiate — Start single-doc hash signing</li>
 * <li>POST /hashsign/bulk/initiate — Start bulk hash signing</li>
 * <li>GET /hashsign/callback — OAuth callback (exchange code → sign)</li>
 * <li>GET /hashsign/status/{jobId} — Poll job status</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/hashsign")
@RequiredArgsConstructor
public class HashSigningController {

    private final SingleHashSignService singleService;
    private final BulkHashSignService bulkService;
    private final SigningJobRepository jobRepository;
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
    // POST /hashsign/initiate — Single document
    // ================================================================

    @PostMapping("/initiate")
    public ResponseEntity<?> initiate(@Valid @RequestBody HashSignInitiateRequest request,
            HttpServletRequest httpRequest) {
        User user = getCurrentUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // SOP1 visitors cannot sign
        if ("SOP1".equalsIgnoreCase(user.getUserType())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "SOP1_NOT_ALLOWED",
                            "message", "Visitors (SOP1) cannot perform legally binding hash signing"));
        }

        try {
            byte[] pdfBytes = Base64.getDecoder().decode(request.getFileBase64());

            // Validate PDF
            if (pdfBytes.length < 5 || pdfBytes[0] != '%' || pdfBytes[1] != 'P'
                    || pdfBytes[2] != 'D' || pdfBytes[3] != 'F') {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "INVALID_PDF", "message", "File is not a valid PDF"));
            }

            HashSignJobDto result = singleService.initiate(user.getId(), pdfBytes, request);
            return ResponseEntity.ok(result);

        } catch (HashSignSdkUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "SDK_UNAVAILABLE", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Hash sign initiation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INITIATE_FAILED", "message", e.getMessage()));
        }
    }

    // ================================================================
    // POST /hashsign/bulk/initiate — Bulk documents
    // ================================================================

    @PostMapping("/bulk/initiate")
    public ResponseEntity<?> initiateBulk(@Valid @RequestBody BulkHashSignRequest request,
            HttpServletRequest httpRequest) {
        User user = getCurrentUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if ("SOP1".equalsIgnoreCase(user.getUserType())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "SOP1_NOT_ALLOWED",
                            "message", "Visitors (SOP1) cannot perform hash signing"));
        }

        try {
            HashSignJobDto result = bulkService.initiateBulk(user.getId(), request.getDocuments());
            return ResponseEntity.ok(Map.of(
                    "jobId", result.getJobId(),
                    "signingUrl", result.getSigningUrl(),
                    "documentCount", request.getDocuments().size(),
                    "note", "Single user approval covers ALL documents in this batch"));
        } catch (HashSignSdkUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "SDK_UNAVAILABLE", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Bulk hash sign initiation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INITIATE_FAILED", "message", e.getMessage()));
        }
    }

    // ================================================================
    // GET /hashsign/callback — OAuth callback
    // ================================================================

    @GetMapping("/callback")
    public void callback(@RequestParam("code") String code,
            @RequestParam("state") String state,
            @RequestParam(value = "error", required = false) String error,
            HttpServletResponse response) throws Exception {

        if (error != null) {
            log.warn("Hash sign callback received error: {}", error);
            response.sendRedirect(frontendUrl + "/hashsign/result?error=" + error);
            return;
        }

        // Consume state — returns StatePayload record
        StatePayload statePayload;
        try {
            statePayload = stateService.consumeState(state);
        } catch (Exception e) {
            log.error("Invalid/expired state in hashsign callback: {}", e.getMessage());
            response.sendRedirect(frontendUrl + "/hashsign/result?error=invalid_state");
            return;
        }

        if (!"HASH_SIGN".equals(statePayload.flowType())) {
            response.sendRedirect(frontendUrl + "/hashsign/result?error=invalid_flow");
            return;
        }

        // Exchange code → access token
        String accessToken = exchangeCodeForToken(code);

        UUID userId = statePayload.userId();
        if (userId == null) {
            response.sendRedirect(frontendUrl + "/hashsign/result?error=no_user");
            return;
        }

        SigningJob job = jobRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(j -> "AWAITING_USER".equals(j.getStatus())
                        && ("HASH".equals(j.getSigningType()) || "HASH_BULK".equals(j.getSigningType())))
                .findFirst()
                .orElse(null);

        if (job == null) {
            response.sendRedirect(frontendUrl + "/hashsign/result?error=no_job");
            return;
        }

        // Complete signing (based on type)
        if ("HASH_BULK".equals(job.getSigningType())) {
            bulkService.completeBulk(job.getId(), accessToken);
        } else {
            singleService.complete(job.getId(), accessToken);
        }

        response.sendRedirect(frontendUrl + "/hashsign/result?jobId=" + job.getId());
    }

    // ================================================================
    // GET /hashsign/status/{jobId}
    // ================================================================

    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> status(@PathVariable UUID jobId,
            HttpServletRequest httpRequest) {
        User user = getCurrentUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SigningJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !job.getUserId().equals(user.getId())) {
            return ResponseEntity.notFound().build();
        }

        String downloadUrl = "SIGNED".equals(job.getStatus())
                ? "/signature/download/" + jobId
                : null;

        return ResponseEntity.ok(SigningJobStatusResponse.builder()
                .status(job.getStatus())
                .downloadUrl(downloadUrl)
                .errorMessage(job.getErrorMessage())
                .ltvApplied(job.getLtvApplied())
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
                    + "&redirect_uri=" + appBaseUrl + "/hashsign/callback";

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
}
