package com.yoursp.uaepass.modules.signature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursp.uaepass.model.entity.SigningJob;
import com.yoursp.uaepass.modules.signature.dto.MultiDocRequest;
import com.yoursp.uaepass.modules.signature.dto.SignInitiateResponse;
import com.yoursp.uaepass.repository.SigningJobRepository;
import com.yoursp.uaepass.service.AuditService;
import com.yoursp.uaepass.service.storage.StorageService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Multi-document signing flow with UAE PASS eSign SP v2 API.
 * <p>
 * Uploads multiple PDFs in a single signer_process and handles per-document
 * status tracking in the signing_jobs.documents JSONB column.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultipleDocSignService {

    private final SpTokenService spTokenService;
    private final SigningJobRepository jobRepository;
    private final StorageService storageService;
    private final LtvService ltvService;
    private final AuditService auditService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${signature.sign-api-base}")
    private String signApiBase;

    @Value("${app.base-url}")
    private String appBaseUrl;

    /**
     * Initiate a multi-document signing process.
     */
    @CircuitBreaker(name = "signCreate", fallbackMethod = "initiateMultiDocFallback")
    public SignInitiateResponse initiateMultiDocSigning(UUID userId, List<MultiDocRequest> docs) {
        String spToken = spTokenService.getSpAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(spToken);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // Process JSON
        Map<String, Object> processJson = buildMultiDocProcessJson();
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        try {
            body.add("process", new HttpEntity<>(objectMapper.writeValueAsString(processJson), jsonHeaders));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize multi-doc process JSON", e);
        }

        // Document parts
        List<Map<String, String>> docMeta = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            MultiDocRequest doc = docs.get(i);
            byte[] pdfBytes = Base64.getDecoder().decode(doc.getFileBase64());

            HttpHeaders pdfHeaders = new HttpHeaders();
            pdfHeaders.setContentType(MediaType.APPLICATION_PDF);
            pdfHeaders.setContentDispositionFormData("document", doc.getFileName());

            final String fileName = doc.getFileName();
            ByteArrayResource resource = new ByteArrayResource(pdfBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            body.add("document", new HttpEntity<>(resource, pdfHeaders));

            docMeta.add(Map.of(
                    "name", doc.getFileName(),
                    "index", String.valueOf(i)));
        }

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        @SuppressWarnings("unchecked")
        ResponseEntity<Map> response = restTemplate.exchange(
                signApiBase + "/signer_processes",
                HttpMethod.POST, entity, Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> respBody = response.getBody();
        if (respBody == null) {
            throw new RuntimeException("Empty response from multi-doc signer_processes");
        }

        String signerProcessId = (String) respBody.get("id");
        String signingUrl = extractSigningUrl(respBody);
        String documentsJson = serializeDocuments(respBody);

        SigningJob job = SigningJob.builder()
                .userId(userId)
                .signerProcessId(signerProcessId)
                .signingType("MULTIPLE")
                .status("INITIATED")
                .documentCount(docs.size())
                .documents(documentsJson)
                .finishCallbackUrl(appBaseUrl + "/signature/callback")
                .initiatedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusMinutes(60))
                .build();

        SigningJob saved = jobRepository.save(job);

        // Store unsigned PDFs
        for (int i = 0; i < docs.size(); i++) {
            byte[] pdfBytes = Base64.getDecoder().decode(docs.get(i).getFileBase64());
            storageService.upload(pdfBytes,
                    "unsigned/" + saved.getId() + "_" + i + ".pdf", "application/pdf");
        }

        auditService.log(userId, "SIGN_INITIATED", "SIGNING_JOB",
                saved.getId().toString(), null,
                Map.of("signerProcessId", signerProcessId,
                        "documentCount", docs.size(),
                        "type", "MULTIPLE"));

        return new SignInitiateResponse(saved.getId(), signingUrl);
    }

    /**
     * Complete multi-doc signing (async).
     */
    @Async("signatureExecutor")
    public void completeMultiDocSign(String signerProcessId, String callbackStatus) {
        log.info("Processing multi-doc completion: signerProcessId={}, status={}",
                signerProcessId, callbackStatus);

        SigningJob job = jobRepository.findBySignerProcessId(signerProcessId).orElse(null);
        if (job == null) {
            log.error("Multi-doc signing job not found: {}", signerProcessId);
            return;
        }

        job.setCallbackStatus(callbackStatus);
        job.setStatus("CALLBACK_RECEIVED");
        jobRepository.save(job);

        if ("canceled".equals(callbackStatus)) {
            job.setStatus("CANCELED");
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);
            return;
        }

        try {
            String spToken = spTokenService.getSpAccessToken();
            List<Map<String, Object>> docs = objectMapper.readValue(job.getDocuments(),
                    new TypeReference<List<Map<String, Object>>>() {
                    });

            job.setStatus("COMPLETING");
            jobRepository.save(job);

            int successCount = 0;
            for (int i = 0; i < docs.size(); i++) {
                Map<String, Object> doc = docs.get(i);
                String docUrl = (String) doc.get("url");
                try {
                    // Download signed doc
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(spToken);
                    ResponseEntity<byte[]> docResponse = restTemplate.exchange(
                            docUrl + "/content", HttpMethod.GET,
                            new HttpEntity<>(headers), byte[].class);

                    byte[] signedPdf = docResponse.getBody();
                    if (signedPdf != null && signedPdf.length > 0) {
                        storageService.upload(signedPdf,
                                "signed/" + job.getId() + "_" + i + ".pdf", "application/pdf");

                        // Apply LTV
                        byte[] ltvPdf = ltvService.applyLtv(signedPdf, job.getId());
                        if (ltvPdf != signedPdf) {
                            storageService.upload(ltvPdf,
                                    "signed-ltv/" + job.getId() + "_" + i + ".pdf", "application/pdf");
                        }
                        doc.put("status", "SIGNED");
                        successCount++;
                    } else {
                        doc.put("status", "FAILED");
                    }
                } catch (Exception e) {
                    log.error("Failed to download/process doc {} for job {}: {}",
                            i, job.getId(), e.getMessage());
                    doc.put("status", "FAILED");
                }
            }

            // Update documents JSON with per-doc status
            job.setDocuments(objectMapper.writeValueAsString(docs));
            job.setStatus(successCount == docs.size() ? "SIGNED" : "FAILED_DOCUMENTS");
            job.setLtvApplied(successCount > 0);
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);

            // Cleanup
            for (Map<String, Object> doc : docs) {
                try {
                    String docUrl = (String) doc.get("url");
                    if (docUrl != null) {
                        HttpHeaders h = new HttpHeaders();
                        h.setBearerAuth(spToken);
                        restTemplate.exchange(docUrl, HttpMethod.DELETE, new HttpEntity<>(h), Void.class);
                    }
                } catch (Exception ignored) {
                }
            }

            auditService.log(job.getUserId(), "SIGN_COMPLETED", "SIGNING_JOB",
                    job.getId().toString(), null,
                    Map.of("type", "MULTIPLE", "successCount", successCount, "totalDocs", docs.size()));

        } catch (Exception e) {
            log.error("Multi-doc completion failed for job {}: {}", job.getId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            jobRepository.save(job);
        }
    }

    private Map<String, Object> buildMultiDocProcessJson() {
        Map<String, Object> process = new LinkedHashMap<>();
        process.put("process_type", "urn:safelayer:eidas:processes:document:sign:esigp");
        process.put("labels", List.of(List.of("digitalid", "server", "qualified")));
        process.put("signer", Map.of(
                "signature_policy_id", "urn:safelayer:eidas:policies:sign:document:pdf",
                "parameters", Map.of("type", "pades-baseline")));
        process.put("ui_locales", List.of("en_US"));
        process.put("finish_callback_url", appBaseUrl + "/signature/callback");
        process.put("timestamp", Map.of("provider_id", "urn:uae:tws:generation:policy:digitalid"));
        return process;
    }

    @SuppressWarnings("unchecked")
    private String extractSigningUrl(Map<String, Object> respBody) {
        try {
            Map<String, Object> tasks = (Map<String, Object>) respBody.get("tasks");
            if (tasks != null) {
                List<Map<String, Object>> pending = (List<Map<String, Object>>) tasks.get("pending");
                if (pending != null && !pending.isEmpty()) {
                    return (String) pending.get(0).get("url");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract signing URL: {}", e.getMessage());
        }
        return signApiBase.replace("/v2", "/v2/ui") + "?signerProcessId=" + respBody.get("id");
    }

    private String serializeDocuments(Map<String, Object> resp) {
        try {
            return objectMapper.writeValueAsString(resp.get("documents"));
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @SuppressWarnings("unused")
    private SignInitiateResponse initiateMultiDocFallback(UUID userId,
            List<MultiDocRequest> docs, Throwable t) {
        throw new RuntimeException("Multi-doc signing unavailable", t);
    }
}
