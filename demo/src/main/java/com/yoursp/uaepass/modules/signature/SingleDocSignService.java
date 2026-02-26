package com.yoursp.uaepass.modules.signature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursp.uaepass.model.entity.SigningJob;
import com.yoursp.uaepass.modules.signature.dto.SignInitiateRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Single-document signing flow with UAE PASS eSign SP v2 API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SingleDocSignService {

    private final SpTokenService spTokenService;
    private final SigningJobRepository jobRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${signature.sign-api-base}")
    private String signApiBase;

    @Value("${app.base-url}")
    private String appBaseUrl;

    /**
     * Initiate a single-document signing process.
     *
     * @param userId   the authenticated user's ID
     * @param pdfBytes raw PDF bytes (already validated)
     * @param params   signature field placement parameters
     * @return jobId + signingUrl for user redirect
     */
    @CircuitBreaker(name = "signCreate", fallbackMethod = "initiateSigningFallback")
    public SignInitiateResponse initiateSigning(UUID userId, byte[] pdfBytes, SignInitiateRequest params) {
        String spToken = spTokenService.getSpAccessToken();

        // Build process JSON
        Map<String, Object> processJson = buildProcessJson(params);

        // Build multipart request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(spToken);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // "process" part — JSON
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        try {
            String processStr = objectMapper.writeValueAsString(processJson);
            body.add("process", new HttpEntity<>(processStr, jsonHeaders));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize process JSON", e);
        }

        // "document" part — PDF binary
        HttpHeaders pdfHeaders = new HttpHeaders();
        pdfHeaders.setContentType(MediaType.APPLICATION_PDF);
        pdfHeaders.setContentDispositionFormData("document", params.getFileName());
        ByteArrayResource pdfResource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return params.getFileName();
            }
        };
        body.add("document", new HttpEntity<>(pdfResource, pdfHeaders));

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        log.info("Creating signer process at {} for user {}", signApiBase + "/signer_processes", userId);

        @SuppressWarnings("unchecked")
        ResponseEntity<Map> response = restTemplate.exchange(
                signApiBase + "/signer_processes",
                HttpMethod.POST,
                entity,
                Map.class);

        if (response.getStatusCode() != HttpStatus.CREATED && response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("UAE PASS signing API returned " + response.getStatusCode());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> respBody = response.getBody();
        if (respBody == null) {
            throw new RuntimeException("Empty response from signer_processes");
        }

        String signerProcessId = (String) respBody.get("id");
        String signingUrl = extractSigningUrl(respBody);
        String documentsJson = serializeDocuments(respBody);

        // Store unsigned PDF
        SigningJob job = SigningJob.builder()
                .userId(userId)
                .signerProcessId(signerProcessId)
                .signingType("SINGLE")
                .status("INITIATED")
                .documentCount(1)
                .documents(documentsJson)
                .finishCallbackUrl(appBaseUrl + "/signature/callback")
                .initiatedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusMinutes(60))
                .build();

        SigningJob saved = jobRepository.save(job);

        // Store unsigned PDF for reference
        storageService.upload(pdfBytes, "unsigned/" + saved.getId() + ".pdf", "application/pdf");

        // Audit
        auditService.log(userId, "SIGN_INITIATED", "SIGNING_JOB",
                saved.getId().toString(), null,
                Map.of("signerProcessId", signerProcessId, "fileName", params.getFileName()));

        log.info("Signing job created: {} (signerProcessId={})", saved.getId(), signerProcessId);

        return new SignInitiateResponse(saved.getId(), signingUrl);
    }

    private Map<String, Object> buildProcessJson(SignInitiateRequest params) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("page", params.getPageNumber());
        location.put("llx", params.getX());
        location.put("lly", params.getY());
        location.put("urx", params.getX() + params.getWidth());
        location.put("ury", params.getY() + params.getHeight());

        Map<String, Object> signatureField = new LinkedHashMap<>();
        signatureField.put("name", params.getSignatureFieldName());
        signatureField.put("location", location);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "pades-baseline");
        parameters.put("signature_field", signatureField);
        if (params.isShowSignatureImage()) {
            parameters.put("appearance", Map.of("show_signature_image", true));
        } else {
            parameters.put("appearance", Collections.emptyMap());
        }

        Map<String, Object> signer = new LinkedHashMap<>();
        signer.put("signature_policy_id", "urn:safelayer:eidas:policies:sign:document:pdf");
        signer.put("parameters", parameters);

        Map<String, Object> process = new LinkedHashMap<>();
        process.put("process_type", "urn:safelayer:eidas:processes:document:sign:esigp");
        process.put("labels", List.of(List.of("digitalid", "server", "qualified")));
        process.put("signer", signer);
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
            log.warn("Failed to extract signing URL from response: {}", e.getMessage());
        }
        // Fallback: construct URL
        String id = (String) respBody.get("id");
        return signApiBase.replace("/v2", "/v2/ui") + "?signerProcessId=" + id;
    }

    private String serializeDocuments(Map<String, Object> respBody) {
        try {
            Object docs = respBody.get("documents");
            if (docs != null) {
                return objectMapper.writeValueAsString(docs);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize documents JSON: {}", e.getMessage());
        }
        return "[]";
    }

    @SuppressWarnings("unused")
    private SignInitiateResponse initiateSigningFallback(UUID userId, byte[] pdfBytes,
            SignInitiateRequest params, Throwable t) {
        log.error("Signing initiation failed (circuit breaker): {}", t.getMessage());
        throw new RuntimeException("Signing service temporarily unavailable", t);
    }
}
