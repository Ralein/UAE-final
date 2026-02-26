package com.yoursp.uaepass.modules.signature;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursp.uaepass.model.entity.SigningJob;
import com.yoursp.uaepass.repository.SigningJobRepository;
import com.yoursp.uaepass.service.AuditService;
import com.yoursp.uaepass.service.storage.StorageService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Async service that completes signing after UAE PASS callback.
 * <ul>
 * <li>Downloads signed PDF from UAE PASS</li>
 * <li>Applies LTV enhancement (mandatory)</li>
 * <li>Stores final PDF in StorageService</li>
 * <li>Cleans up document from UAE PASS</li>
 * </ul>
 */
@SuppressWarnings("null")
@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureCompletionService {

    private final SpTokenService spTokenService;
    private final SigningJobRepository jobRepository;
    private final StorageService storageService;
    private final LtvService ltvService;
    private final AuditService auditService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Process signing completion asynchronously.
     * Called from the callback endpoint — NEVER blocks the callback response.
     */
    @Async("signatureExecutor")
    public void completeSign(String signerProcessId, String callbackStatus) {
        log.info("Processing signing completion: signerProcessId={}, status={}",
                signerProcessId, callbackStatus);

        SigningJob job = jobRepository.findBySignerProcessId(signerProcessId)
                .orElse(null);

        if (job == null) {
            log.error("Signing job not found for signerProcessId: {}", signerProcessId);
            return;
        }

        // Update callback status
        job.setCallbackStatus(callbackStatus);
        job.setStatus("CALLBACK_RECEIVED");
        jobRepository.save(job);

        // Handle non-success statuses
        if (!"finished".equals(callbackStatus)) {
            String failStatus;
            switch (callbackStatus) {
                case "canceled" -> failStatus = "CANCELED";
                case "failed_documents" -> failStatus = "FAILED_DOCUMENTS";
                default -> failStatus = "FAILED";
            }
            job.setStatus(failStatus);
            job.setErrorMessage("Signing " + callbackStatus + " by user or UAE PASS");
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);

            auditService.log(job.getUserId(), "SIGN_" + failStatus, "SIGNING_JOB",
                    job.getId().toString(), null,
                    Map.of("signerProcessId", signerProcessId, "callbackStatus", callbackStatus));

            log.warn("Signing job {} ended with status: {}", job.getId(), failStatus);
            return;
        }

        try {
            // Get SP token for downloading
            String spToken = spTokenService.getSpAccessToken();

            // Extract document URL from stored JSON
            String docUrl = extractDocumentContentUrl(job.getDocuments());
            if (docUrl == null) {
                throw new RuntimeException("No document URL found in job documents");
            }

            // Download signed PDF
            job.setStatus("COMPLETING");
            jobRepository.save(job);

            byte[] signedPdf = downloadSignedDocument(docUrl, spToken);
            log.info("Downloaded signed PDF for job {} ({} bytes)", job.getId(), signedPdf.length);

            // Store signed PDF
            storageService.upload(signedPdf, "signed/" + job.getId() + ".pdf", "application/pdf");

            // Apply LTV enhancement (mandatory per UAE PASS docs)
            byte[] ltvPdf = ltvService.applyLtv(signedPdf, job.getId());
            boolean ltvSuccess = ltvPdf != signedPdf; // reference check — LTV returns new array if success

            if (ltvSuccess) {
                storageService.upload(ltvPdf, "signed-ltv/" + job.getId() + ".pdf", "application/pdf");
            }

            // Update job status
            job.setStatus("SIGNED");
            job.setLtvApplied(ltvSuccess);
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);

            // Cleanup: delete document from UAE PASS
            deleteDocumentFromUaePass(job.getDocuments(), spToken);

            auditService.log(job.getUserId(), "SIGN_COMPLETED", "SIGNING_JOB",
                    job.getId().toString(), null,
                    Map.of("signerProcessId", signerProcessId,
                            "ltvApplied", ltvSuccess));

            log.info("Signing job {} completed successfully (LTV={})", job.getId(), ltvSuccess);

        } catch (Exception e) {
            log.error("Signing completion failed for job {}: {}", job.getId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage("Completion failed: " + e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);

            auditService.log(job.getUserId(), "SIGN_FAILED", "SIGNING_JOB",
                    job.getId().toString(), null,
                    Map.of("error", e.getMessage()));
        }
    }

    @CircuitBreaker(name = "signDownload", fallbackMethod = "downloadFallback")
    private byte[] downloadSignedDocument(String docUrl, String spToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(spToken);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                docUrl + "/content",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class);

        if (response.getBody() == null || response.getBody().length == 0) {
            throw new RuntimeException("Empty signed PDF downloaded from UAE PASS");
        }

        return response.getBody();
    }

    private String extractDocumentContentUrl(String documentsJson) {
        try {
            List<Map<String, Object>> docs = objectMapper.readValue(documentsJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            if (!docs.isEmpty()) {
                return (String) docs.get(0).get("url");
            }
        } catch (Exception e) {
            log.error("Failed to parse documents JSON: {}", e.getMessage());
        }
        return null;
    }

    private void deleteDocumentFromUaePass(String documentsJson, String spToken) {
        try {
            List<Map<String, Object>> docs = objectMapper.readValue(documentsJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            for (Map<String, Object> doc : docs) {
                String docUrl = (String) doc.get("url");
                if (docUrl != null) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(spToken);
                    restTemplate.exchange(docUrl, HttpMethod.DELETE,
                            new HttpEntity<>(headers), Void.class);
                    log.debug("Deleted document from UAE PASS: {}", docUrl);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete document from UAE PASS (non-critical): {}", e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private byte[] downloadFallback(String docUrl, String spToken, Throwable t) {
        throw new RuntimeException("Failed to download signed document (circuit breaker open)", t);
    }
}
