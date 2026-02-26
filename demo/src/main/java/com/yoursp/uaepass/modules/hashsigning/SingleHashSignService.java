package com.yoursp.uaepass.modules.hashsigning;

import com.yoursp.uaepass.model.entity.SigningJob;
import com.yoursp.uaepass.modules.auth.StateService;
import com.yoursp.uaepass.modules.hashsigning.dto.HashSignInitiateRequest;
import com.yoursp.uaepass.modules.hashsigning.dto.HashSignJobDto;
import com.yoursp.uaepass.modules.hashsigning.dto.HashStartResult;
import com.yoursp.uaepass.modules.signature.LtvService;
import com.yoursp.uaepass.repository.SigningJobRepository;
import com.yoursp.uaepass.service.AuditService;
import com.yoursp.uaepass.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Single-document hash signing flow.
 * <ol>
 * <li>SDK /start → get digest + signIdentityId</li>
 * <li>Build hsign-as authorization URL → user approves</li>
 * <li>Callback → exchange code → SDK /sign → LTV → store</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SingleHashSignService {

        private final HashSignSdkClient sdkClient;
        private final StateService stateService;
        private final SigningJobRepository jobRepository;
        private final StorageService storageService;
        private final LtvService ltvService;
        private final AuditService auditService;

        @Value("${uaepass.base-url:https://stg-id.uaepass.ae}")
        private String uaepassBaseUrl;

        @Value("${uaepass.client-id:}")
        private String clientId;

        @Value("${app.base-url:http://localhost:8080}")
        private String appBaseUrl;

        @Value("${hashsign.scope:urn:uae:digitalid:backend_api:hash_signing urn:safelayer:eidas:sign:identity:use:server}")
        private String hashSignScope;

        /**
         * Initiate a single-document hash signing process.
         */
        public HashSignJobDto initiate(UUID userId, byte[] pdfBytes, HashSignInitiateRequest params) {
                // Build signProp: "{page}:[{x},{y},{width},{height}]"
                String signProp = params.getPageNumber() + ":["
                                + params.getX() + "," + params.getY() + ","
                                + params.getWidth() + "," + params.getHeight() + "]";

                // Step 1: Call SDK /start
                HashStartResult startResult = sdkClient.startProcess(pdfBytes, signProp);

                // Create signing job
                SigningJob job = SigningJob.builder()
                                .userId(userId)
                                .signingType("HASH")
                                .status("INITIATED")
                                .signIdentityId(startResult.getSignIdentityId())
                                .documentCount(1)
                                .initiatedAt(OffsetDateTime.now())
                                .expiresAt(OffsetDateTime.now().plusHours(1))
                                .build();
                SigningJob saved = jobRepository.save(job);

                // Store unsigned PDF
                storageService.upload(pdfBytes,
                                "hashsign/unsigned/" + saved.getId() + ".pdf", "application/pdf");

                // Store txId + digest in documents JSONB for later retrieval
                saved.setDocuments(
                                "{\"txId\":\"" + startResult.getTxId()
                                                + "\",\"digest\":\"" + startResult.getDigest()
                                                + "\",\"signProp\":\"" + signProp + "\"}");

                // Step 2: Generate state and build auth URL
                String state = stateService.generateState("HASH_SIGN", null, userId);

                String signingUrl = UriComponentsBuilder
                                .fromUriString(uaepassBaseUrl + "/trustedx-authserver/oauth/hsign-as")
                                .queryParam("response_type", "code")
                                .queryParam("client_id", clientId)
                                .queryParam("redirect_uri", appBaseUrl + "/hashsign/callback")
                                .queryParam("state", state)
                                .queryParam("scope", hashSignScope)
                                .queryParam("digests_summary", startResult.getDigest())
                                .queryParam("digests_summary_algorithm", "SHA256")
                                .queryParam("sign_identity_id", startResult.getSignIdentityId())
                                .queryParam("signProp", signProp)
                                .build()
                                .toUriString();

                saved.setStatus("AWAITING_USER");
                saved.setFinishCallbackUrl(appBaseUrl + "/hashsign/callback");
                jobRepository.save(saved);

                auditService.log(userId, "HASH_SIGN_INITIATED", "SigningJob",
                                saved.getId().toString(), null,
                                Map.of("signingType", "HASH", "txId", startResult.getTxId()));

                log.info("Hash sign initiated: jobId={}, txId={}", saved.getId(), startResult.getTxId());

                return new HashSignJobDto(saved.getId(), signingUrl);
        }

        /**
         * Complete single-document hash signing after callback.
         */
        public void complete(UUID jobId, String accessToken) {
                SigningJob job = jobRepository.findById(jobId)
                                .orElseThrow(() -> new RuntimeException("Signing job not found: " + jobId));

                job.setStatus("CALLBACK_RECEIVED");
                jobRepository.save(job);

                try {
                        // Parse txId and signIdentityId from documents JSONB
                        String docs = job.getDocuments();
                        String txId = extractJsonField(docs, "txId");
                        String signIdentityId = job.getSignIdentityId();

                        // Step 3: Call SDK /sign with access token
                        byte[] signedPdf = sdkClient.signDocument(txId, signIdentityId, accessToken);

                        // Store signed PDF
                        storageService.upload(signedPdf,
                                        "hashsign/signed/" + jobId + ".pdf", "application/pdf");

                        job.setStatus("COMPLETING");
                        jobRepository.save(job);

                        // Apply LTV (mandatory)
                        byte[] ltvPdf = ltvService.applyLtv(signedPdf, jobId);
                        boolean ltvApplied = ltvPdf != signedPdf;

                        if (ltvApplied) {
                                storageService.upload(ltvPdf,
                                                "hashsign/signed-ltv/" + jobId + ".pdf", "application/pdf");
                        }

                        job.setStatus("SIGNED");
                        job.setLtvApplied(ltvApplied);
                        job.setCompletedAt(OffsetDateTime.now());
                        jobRepository.save(job);

                        auditService.log(job.getUserId(), "HASH_SIGN_COMPLETED", "SigningJob",
                                        jobId.toString(), null,
                                        Map.of("ltvApplied", ltvApplied, "txId", txId));

                        log.info("Hash sign completed: jobId={}, ltvApplied={}", jobId, ltvApplied);

                } catch (Exception e) {
                        log.error("Hash sign completion failed: jobId={}, error={}", jobId, e.getMessage());
                        job.setStatus("FAILED");
                        job.setErrorMessage(e.getMessage());
                        job.setCompletedAt(OffsetDateTime.now());
                        jobRepository.save(job);
                }
        }

        private String extractJsonField(String json, String field) {
                String key = "\"" + field + "\":\"";
                int start = json.indexOf(key);
                if (start < 0)
                        return null;
                start += key.length();
                int end = json.indexOf("\"", start);
                return end > start ? json.substring(start, end) : null;
        }
}
