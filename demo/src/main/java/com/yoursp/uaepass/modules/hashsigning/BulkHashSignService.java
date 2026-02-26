package com.yoursp.uaepass.modules.hashsigning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursp.uaepass.model.entity.SigningJob;
import com.yoursp.uaepass.modules.auth.StateService;
import com.yoursp.uaepass.modules.hashsigning.dto.BulkDoc;
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

import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Bulk hash signing — multiple documents in one user approval.
 * <p>
 * digests_summary = SHA-256(concat(digest1_bytes, digest2_bytes, ...))
 * signProp = "1:[x1,y1,w1,h1]|2:[x2,y2,w2,h2]"
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class BulkHashSignService {

    private final HashSignSdkClient sdkClient;
    private final StateService stateService;
    private final SigningJobRepository jobRepository;
    private final StorageService storageService;
    private final LtvService ltvService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${uaepass.base-url:https://stg-id.uaepass.ae}")
    private String uaepassBaseUrl;

    @Value("${uaepass.client-id:}")
    private String clientId;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Value("${hashsign.scope:urn:uae:digitalid:backend_api:hash_signing urn:safelayer:eidas:sign:identity:use:server}")
    private String hashSignScope;

    /**
     * Initiate bulk hash signing.
     */
    public HashSignJobDto initiateBulk(UUID userId, List<BulkDoc> docs) {
        List<Map<String, Object>> docEntries = new ArrayList<>();
        StringBuilder signPropBuilder = new StringBuilder();
        List<byte[]> digestBytesList = new ArrayList<>();

        // For each document: SDK /start → collect digest
        for (int i = 0; i < docs.size(); i++) {
            BulkDoc doc = docs.get(i);
            byte[] pdfBytes = Base64.getDecoder().decode(doc.getFileBase64());

            String signProp = doc.getPageNumber() + ":["
                    + doc.getX() + "," + doc.getY() + ","
                    + doc.getWidth() + "," + doc.getHeight() + "]";

            HashStartResult startResult = sdkClient.startProcess(pdfBytes, signProp);

            // Collect per-document info
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", i);
            entry.put("fileName", doc.getFileName());
            entry.put("txId", startResult.getTxId());
            entry.put("signIdentityId", startResult.getSignIdentityId());
            entry.put("digest", startResult.getDigest());
            entry.put("signProp", signProp);
            entry.put("status", "PENDING");
            docEntries.add(entry);

            // Store unsigned PDF
            // (jobId not yet known — use txId as temporary key)
            storageService.upload(pdfBytes,
                    "hashsign/unsigned/" + startResult.getTxId() + ".pdf", "application/pdf");

            // Build combined signProp
            if (i > 0)
                signPropBuilder.append("|");
            signPropBuilder.append(signProp);

            // Convert hex digest to bytes for combined hash
            digestBytesList.add(hexToBytes(startResult.getDigest()));
        }

        // Compute combined digests_summary
        String digestsSummary = computeDigestsSummary(digestBytesList);

        // Use first doc's signIdentityId (all should be same)
        String signIdentityId = docEntries.get(0).get("signIdentityId").toString();

        // Create signing job
        SigningJob job = SigningJob.builder()
                .userId(userId)
                .signingType("HASH_BULK")
                .status("INITIATED")
                .signIdentityId(signIdentityId)
                .documentCount(docs.size())
                .initiatedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .build();

        try {
            job.setDocuments(objectMapper.writeValueAsString(docEntries));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize document entries", e);
        }

        SigningJob saved = jobRepository.save(job);

        // Generate state
        String state = stateService.generateState("HASH_SIGN", null, userId);

        String signingUrl = UriComponentsBuilder
                .fromUriString(uaepassBaseUrl + "/trustedx-authserver/oauth/hsign-as")
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", appBaseUrl + "/hashsign/callback")
                .queryParam("state", state)
                .queryParam("scope", hashSignScope)
                .queryParam("digests_summary", digestsSummary)
                .queryParam("digests_summary_algorithm", "SHA256")
                .queryParam("sign_identity_id", signIdentityId)
                .queryParam("signProp", signPropBuilder.toString())
                .build()
                .toUriString();

        saved.setStatus("AWAITING_USER");
        saved.setFinishCallbackUrl(appBaseUrl + "/hashsign/callback");
        jobRepository.save(saved);

        auditService.log(userId, "HASH_SIGN_BULK_INITIATED", "SigningJob",
                saved.getId().toString(), null,
                Map.of("documentCount", docs.size()));

        log.info("Bulk hash sign initiated: jobId={}, docCount={}", saved.getId(), docs.size());

        return new HashSignJobDto(saved.getId(), signingUrl);
    }

    /**
     * Complete bulk hash signing after callback.
     */
    public void completeBulk(UUID jobId, String accessToken) {
        SigningJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Signing job not found: " + jobId));

        job.setStatus("CALLBACK_RECEIVED");
        jobRepository.save(job);

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> docEntries = objectMapper.readValue(
                    job.getDocuments(), List.class);

            boolean allSuccess = true;

            for (Map<String, Object> entry : docEntries) {
                String txId = (String) entry.get("txId");
                String signIdentityId = (String) entry.get("signIdentityId");

                try {
                    byte[] signedPdf = sdkClient.signDocument(txId, signIdentityId, accessToken);

                    storageService.upload(signedPdf,
                            "hashsign/signed/" + txId + ".pdf", "application/pdf");

                    // Apply LTV
                    byte[] ltvPdf = ltvService.applyLtv(signedPdf, jobId);
                    boolean ltvApplied = ltvPdf != signedPdf;

                    if (ltvApplied) {
                        storageService.upload(ltvPdf,
                                "hashsign/signed-ltv/" + txId + ".pdf", "application/pdf");
                    }

                    entry.put("status", "SIGNED");
                    entry.put("signedKey", "hashsign/signed/" + txId + ".pdf");
                    entry.put("ltvKey", ltvApplied ? "hashsign/signed-ltv/" + txId + ".pdf" : null);

                } catch (Exception e) {
                    log.error("Bulk sign failed for txId={}: {}", txId, e.getMessage());
                    entry.put("status", "FAILED");
                    entry.put("error", e.getMessage());
                    allSuccess = false;
                }
            }

            job.setDocuments(objectMapper.writeValueAsString(docEntries));
            job.setStatus(allSuccess ? "SIGNED" : "FAILED_DOCUMENTS");
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);

            auditService.log(job.getUserId(), "HASH_SIGN_BULK_COMPLETED", "SigningJob",
                    jobId.toString(), null,
                    Map.of("allSuccess", allSuccess, "documentCount", docEntries.size()));

            log.info("Bulk hash sign completed: jobId={}, allSuccess={}", jobId, allSuccess);

        } catch (Exception e) {
            log.error("Bulk hash sign completion failed: jobId={}, error={}", jobId, e.getMessage());
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);
        }
    }

    /**
     * Compute combined digests_summary:
     * SHA-256(concat(digest1_bytes, digest2_bytes, ...))
     */
    static String computeDigestsSummary(List<byte[]> digestBytesList) {
        try {
            // Concatenate all digest byte arrays
            int totalLen = digestBytesList.stream().mapToInt(b -> b.length).sum();
            byte[] combined = new byte[totalLen];
            int offset = 0;
            for (byte[] digestBytes : digestBytesList) {
                System.arraycopy(digestBytes, 0, combined, offset, digestBytes.length);
                offset += digestBytes.length;
            }

            // SHA-256 of the combined bytes
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(combined);

            // Convert to hex string
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to compute digests_summary", e);
        }
    }

    static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
