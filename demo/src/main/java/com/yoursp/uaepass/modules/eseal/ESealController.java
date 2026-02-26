package com.yoursp.uaepass.modules.eseal;

import com.yoursp.uaepass.model.entity.EsealJob;
import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.eseal.dto.*;
import com.yoursp.uaepass.repository.EsealJobRepository;
import com.yoursp.uaepass.service.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for UAE PASS eSeal operations (PAdES + CAdES).
 *
 * <h3>Endpoints:</h3>
 * <ul>
 * <li>POST /eseal/pdf — Seal a PDF (PAdES)</li>
 * <li>POST /eseal/document — Seal any document (CAdES)</li>
 * <li>POST /eseal/verify — Verify an eSeal</li>
 * <li>GET /eseal/download/{jobId} — Download sealed document</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/eseal")
@RequiredArgsConstructor
public class ESealController {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final PadesESealService padesService;
    private final CadesESealService cadesService;
    private final ESealVerificationService verificationService;
    private final EsealJobRepository jobRepository;
    private final StorageService storageService;

    // ================================================================
    // POST /eseal/pdf — PAdES seal
    // ================================================================

    @PostMapping("/pdf")
    public ResponseEntity<?> sealPdf(@RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        User user = getCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Validate file
        ResponseEntity<?> validation = validateFile(file, true);
        if (validation != null)
            return validation;

        try {
            byte[] pdfBytes = file.getBytes();
            ESealResult result = padesService.sealPdf(pdfBytes, user.getId());

            return ResponseEntity.ok(new ESealResponse(
                    result.getJobId(),
                    "/eseal/download/" + result.getJobId()));
        } catch (ESealUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "ESEAL_UNAVAILABLE", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("PAdES sealing failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "SEAL_FAILED", "message", e.getMessage()));
        }
    }

    // ================================================================
    // POST /eseal/document — CAdES seal
    // ================================================================

    @PostMapping("/document")
    public ResponseEntity<?> sealDocument(@RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        User user = getCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Validate file (no PDF check for CAdES)
        ResponseEntity<?> validation = validateFile(file, false);
        if (validation != null)
            return validation;

        try {
            byte[] docBytes = file.getBytes();
            ESealResult result = cadesService.sealDocument(docBytes, user.getId());

            return ResponseEntity.ok(new CadesESealResponse(
                    result.getJobId(),
                    "/eseal/download/" + result.getJobId() + "?type=document",
                    "/eseal/download/" + result.getJobId() + "?type=signature"));
        } catch (ESealUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "ESEAL_UNAVAILABLE", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("CAdES sealing failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "SEAL_FAILED", "message", e.getMessage()));
        }
    }

    // ================================================================
    // POST /eseal/verify — auto-detect PAdES vs CAdES
    // ================================================================

    @PostMapping("/verify")
    public ResponseEntity<ESealVerifyResult> verify(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "signature", required = false) MultipartFile signatureFile) {
        try {
            byte[] docBytes = file.getBytes();

            // Auto-detect: if no separate signature and file is PDF → PAdES, else → CAdES
            boolean isPdf = isPdfFile(docBytes);

            ESealVerifyResult result;
            if (isPdf && signatureFile == null) {
                result = verificationService.verifyPadesESeal(docBytes);
            } else if (signatureFile != null) {
                byte[] sigBytes = signatureFile.getBytes();
                result = verificationService.verifyCadesESeal(docBytes, sigBytes);
            } else {
                return ResponseEntity.badRequest().body(ESealVerifyResult.builder()
                        .valid(false)
                        .resultMajor("Error")
                        .resultMessage("Non-PDF documents require a separate 'signature' file (PKCS#7 .p7s)")
                        .build());
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("eSeal verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ESealVerifyResult.builder()
                            .valid(false)
                            .resultMajor("Error")
                            .resultMessage(e.getMessage())
                            .build());
        }
    }

    // ================================================================
    // GET /eseal/download/{jobId}
    // ================================================================

    @GetMapping("/download/{jobId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID jobId,
            @RequestParam(value = "type", defaultValue = "output") String type,
            HttpServletRequest request) {
        User user = getCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        EsealJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !job.getRequestedBy().equals(user.getId())) {
            return ResponseEntity.notFound().build();
        }

        if (!"SEALED".equals(job.getStatus())) {
            return ResponseEntity.badRequest().build();
        }

        String key;
        String contentType;
        String filename;

        if ("document".equals(type) && "CADES".equals(job.getSealType())) {
            // Download original document for CAdES
            key = job.getInputKey();
            contentType = "application/octet-stream";
            filename = "document_" + jobId + ".bin";
        } else if ("signature".equals(type) && "CADES".equals(job.getSealType())) {
            // Download PKCS#7 signature
            key = job.getOutputKey();
            contentType = "application/pkcs7-signature";
            filename = "signature_" + jobId + ".p7s";
        } else {
            // Default: sealed PDF for PAdES
            key = job.getOutputKey();
            contentType = "application/pdf";
            filename = "sealed_" + jobId + ".pdf";
        }

        byte[] bytes = storageService.download(key);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename)
                .build());

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    // ================================================================
    // Private helpers
    // ================================================================

    private User getCurrentUser(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    private ResponseEntity<?> validateFile(MultipartFile file, boolean requirePdf) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "FILE_REQUIRED", "message", "File is required"));
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "FILE_TOO_LARGE",
                            "message", "File exceeds maximum size of 10MB"));
        }

        if (requirePdf) {
            try {
                byte[] bytes = file.getBytes();
                if (!isPdfFile(bytes)) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "INVALID_PDF",
                                    "message", "File does not appear to be a valid PDF"));
                }
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "FILE_READ_ERROR", "message", "Cannot read file"));
            }
        }

        return null;
    }

    private boolean isPdfFile(byte[] bytes) {
        return bytes.length >= 5
                && bytes[0] == '%' && bytes[1] == 'P'
                && bytes[2] == 'D' && bytes[3] == 'F';
    }
}
