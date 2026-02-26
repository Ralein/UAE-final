package com.yoursp.uaepass.modules.signature;

import com.yoursp.uaepass.modules.face.FaceVerified;
import com.yoursp.uaepass.model.entity.SigningJob;
import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.signature.dto.SignInitiateRequest;
import com.yoursp.uaepass.modules.signature.dto.SignInitiateResponse;
import com.yoursp.uaepass.modules.signature.dto.SigningJobStatusResponse;
import com.yoursp.uaepass.modules.signature.dto.VerificationResult;
import com.yoursp.uaepass.repository.SigningJobRepository;
import com.yoursp.uaepass.service.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for UAE PASS Digital Signature operations.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 * <li>POST /signature/initiate — Start single-doc signing</li>
 * <li>GET /signature/callback — UAE PASS callback (async processing)</li>
 * <li>GET /signature/status/{jobId} — Poll job status</li>
 * <li>GET /signature/download/{jobId} — Download signed PDF</li>
 * <li>POST /signature/verify — Verify a signed PDF</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/signature")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SignatureController {

    private final SingleDocSignService singleDocSignService;
    private final SignatureCompletionService completionService;
    private final SignatureVerificationService verificationService;
    private final SigningJobRepository jobRepository;
    private final StorageService storageService;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    // ================================================================
    // POST /signature/initiate
    // ================================================================

    @FaceVerified
    @PostMapping("/initiate")
    public ResponseEntity<?> initiate(@Valid @RequestBody SignInitiateRequest request,
            HttpServletRequest httpRequest) {
        User user = getCurrentUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // SOP1 visitors cannot sign legally binding documents
        if ("SOP1".equals(user.getUserType())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "SOP1_NOT_ALLOWED",
                    "message", "Visitors (SOP1) cannot sign legally binding documents. " +
                            "Only SOP2 (residents) and SOP3 (citizens) are eligible."));
        }

        // Decode and validate PDF
        byte[] pdfBytes;
        try {
            pdfBytes = Base64.getDecoder().decode(request.getFileBase64());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_BASE64",
                    "message", "fileBase64 is not valid Base64"));
        }

        // Validate PDF header (%PDF)
        if (pdfBytes.length < 5 || pdfBytes[0] != '%' || pdfBytes[1] != 'P'
                || pdfBytes[2] != 'D' || pdfBytes[3] != 'F') {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_PDF",
                    "message", "File does not appear to be a valid PDF"));
        }

        SignInitiateResponse response = singleDocSignService.initiateSigning(
                user.getId(), pdfBytes, request);

        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /signature/callback — UAE PASS finish callback
    // ================================================================

    @GetMapping("/callback")
    public void callback(@RequestParam("status") String status,
            @RequestParam("signer_process_id") String signerProcessId,
            HttpServletResponse response) throws Exception {

        log.info("Signature callback received: signerProcessId={}, status={}", signerProcessId, status);

        // Enqueue async processing — NEVER block the callback
        completionService.completeSign(signerProcessId, status);

        // Redirect user browser to frontend result page
        String redirectUrl;
        if ("finished".equals(status)) {
            redirectUrl = frontendUrl + "/signature/result?status=success&processId=" + signerProcessId;
        } else {
            redirectUrl = frontendUrl + "/signature/result?status=" + status + "&processId=" + signerProcessId;
        }
        response.sendRedirect(redirectUrl);
    }

    // ================================================================
    // GET /signature/status/{jobId}
    // ================================================================

    @GetMapping("/status/{jobId}")
    public ResponseEntity<SigningJobStatusResponse> status(@PathVariable UUID jobId,
            HttpServletRequest httpRequest) {
        User user = getCurrentUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SigningJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !job.getUserId().equals(user.getId())) {
            return ResponseEntity.notFound().build();
        }

        String downloadUrl = null;
        if ("SIGNED".equals(job.getStatus())) {
            downloadUrl = "/signature/download/" + jobId;
        }

        return ResponseEntity.ok(SigningJobStatusResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .downloadUrl(downloadUrl)
                .errorMessage(job.getErrorMessage())
                .ltvApplied(Boolean.TRUE.equals(job.getLtvApplied()))
                .build());
    }

    // ================================================================
    // GET /signature/download/{jobId}
    // ================================================================

    @GetMapping("/download/{jobId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID jobId,
            HttpServletRequest httpRequest) {
        User user = getCurrentUser(httpRequest);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SigningJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !job.getUserId().equals(user.getId())) {
            return ResponseEntity.notFound().build();
        }

        if (!"SIGNED".equals(job.getStatus())) {
            return ResponseEntity.badRequest().build();
        }

        // Prefer LTV-enhanced version
        String key = Boolean.TRUE.equals(job.getLtvApplied())
                ? "signed-ltv/" + jobId + ".pdf"
                : "signed/" + jobId + ".pdf";

        byte[] pdfBytes = storageService.download(key);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("signed_" + jobId + ".pdf")
                .build());

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    // ================================================================
    // POST /signature/verify
    // ================================================================

    @PostMapping("/verify")
    public ResponseEntity<VerificationResult> verify(@RequestParam("file") MultipartFile file) {
        try {
            byte[] pdfBytes = file.getBytes();
            VerificationResult result = verificationService.verifySignature(pdfBytes);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(VerificationResult.builder()
                            .valid(false)
                            .resultMajor("Error")
                            .resultMinor(e.getMessage())
                            .build());
        }
    }

    // ================================================================
    // Private helpers
    // ================================================================

    private User getCurrentUser(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }
}
