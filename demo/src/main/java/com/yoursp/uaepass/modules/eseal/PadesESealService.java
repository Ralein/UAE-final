package com.yoursp.uaepass.modules.eseal;

import com.yoursp.uaepass.model.entity.EsealJob;
import com.yoursp.uaepass.modules.eseal.dto.ESealResult;
import com.yoursp.uaepass.repository.EsealJobRepository;
import com.yoursp.uaepass.service.AuditService;
import com.yoursp.uaepass.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * PAdES eSeal service — seals PDF documents using UAE PASS SOAP API.
 * <p>
 * Profile: {@code urn:safelayer:tws:dss:1.0:profiles:pades:1.0:sign}
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PadesESealService {

    private final ESealSoapClient soapClient;
    private final EsealJobRepository jobRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    @Value("${eseal.soap-endpoint:}")
    private String soapEndpoint;

    @Value("${eseal.cert-subject-name:}")
    private String certSubjectName;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Seal a PDF document with an organizational eSeal (PAdES).
     *
     * @param pdfBytes    the PDF document bytes
     * @param requestedBy the user who requested the seal
     * @return result containing sealed PDF bytes and job ID
     */
    public ESealResult sealPdf(byte[] pdfBytes, UUID requestedBy) {
        if (soapEndpoint == null || soapEndpoint.isBlank()) {
            throw new ESealUnavailableException("ESEAL_SOAP_ENDPOINT is not configured");
        }

        String requestId = generateRequestId();
        String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);

        log.info("Initiating PAdES eSeal: requestId={}, pdfSize={} bytes", requestId, pdfBytes.length);

        // Build SOAP body (SignRequest only — envelope is added by ESealSoapClient)
        String signRequestBody = buildPadesSignRequest(base64Pdf, requestId);

        // Execute SOAP call
        String responseXml = soapClient.executeSoapRequest(soapEndpoint, signRequestBody, requestId);

        // Parse response
        String resultMajor = extractTag(responseXml, "ResultMajor");
        if (resultMajor == null || !resultMajor.contains("Success")) {
            String resultMinor = extractTag(responseXml, "ResultMinor");
            String message = ESealErrorCodeMapper.toMessage(resultMinor);
            log.error("PAdES eSeal failed: requestId={}, resultMajor={}, resultMinor={}",
                    requestId, resultMajor, resultMinor);

            // Record failed job
            EsealJob failedJob = EsealJob.builder()
                    .requestedBy(requestedBy)
                    .sealType("PADES")
                    .status("FAILED")
                    .requestId(requestId)
                    .errorMessage(message)
                    .completedAt(OffsetDateTime.now())
                    .build();
            jobRepository.save(failedJob);

            throw new RuntimeException("PAdES eSeal failed: " + message);
        }

        // Extract sealed PDF from response
        byte[] sealedPdf = extractSealedPdf(responseXml);

        // Persist
        EsealJob job = EsealJob.builder()
                .requestedBy(requestedBy)
                .sealType("PADES")
                .status("SEALED")
                .requestId(requestId)
                .completedAt(OffsetDateTime.now())
                .build();
        EsealJob saved = jobRepository.save(job);

        // Store input and output
        storageService.upload(pdfBytes, "eseal/input/" + saved.getId() + ".pdf", "application/pdf");
        storageService.upload(sealedPdf, "eseal/" + saved.getId() + ".pdf", "application/pdf");

        saved.setInputKey("eseal/input/" + saved.getId() + ".pdf");
        saved.setOutputKey("eseal/" + saved.getId() + ".pdf");
        jobRepository.save(saved);

        auditService.log(requestedBy, "ESEAL_PDF", "ESEAL_JOB",
                saved.getId().toString(), null,
                Map.of("requestId", requestId, "sealType", "PADES"));

        log.info("PAdES eSeal completed: jobId={}, requestId={}", saved.getId(), requestId);

        return ESealResult.builder()
                .jobId(saved.getId())
                .sealedBytes(sealedPdf)
                .requestId(requestId)
                .build();
    }

    private String buildPadesSignRequest(String base64Pdf, String requestId) {
        return """
                <SignRequest xmlns="http://www.docs.oasis-open.org/dss/2004/06/oasis-dss-1.0-core-schema-wd-27.xsd"
                             Profile="urn:safelayer:tws:dss:1.0:profiles:pades:1.0:sign"
                             RequestID="%s">
                  <OptionalInputs>
                    <KeySelector>
                      <ns1:KeySelector xmlns:ns1="http://www.safelayer.com/TWS">
                        <ns1:Name Format="urn:oasis:names:tc:SAML:1.1:nameid-format:X509SubjectName">%s</ns1:Name>
                        <ns1:KeyUsage>nonRepudiation</ns1:KeyUsage>
                      </ns1:KeySelector>
                    </KeySelector>
                  </OptionalInputs>
                  <InputDocuments>
                    <Document>
                      <Base64Data MimeType="application/pdf">%s</Base64Data>
                    </Document>
                  </InputDocuments>
                </SignRequest>
                """.formatted(requestId, certSubjectName, base64Pdf);
    }

    private byte[] extractSealedPdf(String xml) {
        // PAdES response: DocumentWithSignature/XMLData/Base64Data
        String base64 = extractTag(xml, "Base64Data");
        if (base64 == null || base64.isBlank()) {
            throw new RuntimeException("No Base64Data found in PAdES eSeal response");
        }
        // Clean whitespace from base64
        base64 = base64.replaceAll("\\s+", "");
        return Base64.getDecoder().decode(base64);
    }

    private String extractTag(String xml, String tagName) {
        // Try simple tag first
        String open = "<" + tagName + ">";
        int startIdx = xml.indexOf(open);
        if (startIdx >= 0) {
            startIdx += open.length();
        } else {
            // Try tag with attributes
            String altOpen = "<" + tagName + " ";
            startIdx = xml.indexOf(altOpen);
            if (startIdx >= 0) {
                startIdx = xml.indexOf(">", startIdx) + 1;
            }
        }

        // Also try with namespace prefix (e.g., dss:ResultMajor)
        if (startIdx < 0) {
            int nsIdx = xml.indexOf(":" + tagName + ">");
            if (nsIdx >= 0) {
                startIdx = nsIdx + tagName.length() + 2;
            } else {
                nsIdx = xml.indexOf(":" + tagName + " ");
                if (nsIdx >= 0) {
                    startIdx = xml.indexOf(">", nsIdx) + 1;
                }
            }
        }

        String close = "</" + tagName + ">";
        int endIdx = xml.indexOf(close);
        if (endIdx < 0) {
            // Try namespaced close
            int nsClose = xml.indexOf(":" + tagName + ">");
            if (nsClose > 0 && xml.charAt(nsClose - 1) == '/') {
                // self-closing
                return null;
            }
            // Search for closing with namespace
            String pattern = ":" + tagName + ">";
            int lastIdx = xml.lastIndexOf(pattern);
            if (lastIdx > startIdx) {
                // Walk back to find the '</'
                int slashIdx = xml.lastIndexOf("</", lastIdx);
                if (slashIdx >= 0) {
                    endIdx = slashIdx;
                }
            }
        }

        if (startIdx >= 0 && endIdx > startIdx) {
            return xml.substring(startIdx, endIdx).trim();
        }
        return null;
    }

    private String generateRequestId() {
        byte[] bytes = new byte[10];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(20);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
