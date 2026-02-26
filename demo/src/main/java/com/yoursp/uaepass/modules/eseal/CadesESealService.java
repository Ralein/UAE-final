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
 * CAdES eSeal service — seals non-PDF documents using UAE PASS SOAP API.
 * <p>
 * Profile: {@code urn:safelayer:tws:dss:1.0:profiles:cmspkcs7sig:1.0:sign}
 * <br>
 * Returns a PKCS#7 CMS detached signature (not the document itself).
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CadesESealService {

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
     * Seal a document with a CAdES eSeal. Returns the PKCS#7 signature bytes
     * (the original document is stored separately).
     *
     * @param documentBytes the document to seal (any format)
     * @param requestedBy   the user who requested the seal
     * @return result containing PKCS#7 signature bytes and job ID
     */
    public ESealResult sealDocument(byte[] documentBytes, UUID requestedBy) {
        if (soapEndpoint == null || soapEndpoint.isBlank()) {
            throw new ESealUnavailableException("ESEAL_SOAP_ENDPOINT is not configured");
        }

        String requestId = generateRequestId();
        String base64Doc = Base64.getEncoder().encodeToString(documentBytes);

        log.info("Initiating CAdES eSeal: requestId={}, docSize={} bytes", requestId, documentBytes.length);

        String signRequestBody = buildCadesSignRequest(base64Doc, requestId);
        String responseXml = soapClient.executeSoapRequest(soapEndpoint, signRequestBody, requestId);

        // Parse response
        String resultMajor = extractTag(responseXml, "ResultMajor");
        if (resultMajor == null || !resultMajor.contains("Success")) {
            String resultMinor = extractTag(responseXml, "ResultMinor");
            String message = ESealErrorCodeMapper.toMessage(resultMinor);
            log.error("CAdES eSeal failed: requestId={}, resultMajor={}, resultMinor={}",
                    requestId, resultMajor, resultMinor);

            EsealJob failedJob = EsealJob.builder()
                    .requestedBy(requestedBy)
                    .sealType("CADES")
                    .status("FAILED")
                    .requestId(requestId)
                    .errorMessage(message)
                    .completedAt(OffsetDateTime.now())
                    .build();
            jobRepository.save(failedJob);

            throw new RuntimeException("CAdES eSeal failed: " + message);
        }

        // Extract PKCS#7 signature from Base64Signature
        byte[] signatureBytes = extractCadesSignature(responseXml);

        // Persist
        EsealJob job = EsealJob.builder()
                .requestedBy(requestedBy)
                .sealType("CADES")
                .status("SEALED")
                .requestId(requestId)
                .completedAt(OffsetDateTime.now())
                .build();
        EsealJob saved = jobRepository.save(job);

        // Store original document + PKCS#7 signature
        storageService.upload(documentBytes, "eseal/" + saved.getId() + ".bin", "application/octet-stream");
        storageService.upload(signatureBytes, "eseal/" + saved.getId() + ".p7s", "application/pkcs7-signature");

        saved.setInputKey("eseal/" + saved.getId() + ".bin");
        saved.setOutputKey("eseal/" + saved.getId() + ".p7s");
        jobRepository.save(saved);

        auditService.log(requestedBy, "ESEAL_DOCUMENT", "ESEAL_JOB",
                saved.getId().toString(), null,
                Map.of("requestId", requestId, "sealType", "CADES"));

        log.info("CAdES eSeal completed: jobId={}, requestId={}", saved.getId(), requestId);

        return ESealResult.builder()
                .jobId(saved.getId())
                .sealedBytes(signatureBytes)
                .requestId(requestId)
                .build();
    }

    private String buildCadesSignRequest(String base64Doc, String requestId) {
        return """
                <SignRequest xmlns="http://www.docs.oasis-open.org/dss/2004/06/oasis-dss-1.0-core-schema-wd-27.xsd"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                             Profile="urn:safelayer:tws:dss:1.0:profiles:cmspkcs7sig:1.0:sign"
                             RequestID="%s">
                  <OptionalInputs>
                    <KeySelector>
                      <ns1:KeySelector xmlns:ns1="http://www.safelayer.com/TWS">
                        <ns1:Name Format="urn:oasis:names:tc:SAML:1.1:nameid-format:X509SubjectName">%s</ns1:Name>
                        <ns1:KeyUsage>nonRepudiation</ns1:KeyUsage>
                      </ns1:KeySelector>
                    </KeySelector>
                    <SignatureType xsi:type="xsd:anyURI">urn:etsi:ts:101733</SignatureType>
                    <EnvelopingSignature/>
                  </OptionalInputs>
                  <InputDocuments>
                    <Document>
                      <Base64Data>%s</Base64Data>
                    </Document>
                  </InputDocuments>
                </SignRequest>
                """.formatted(requestId, certSubjectName, base64Doc);
    }

    private byte[] extractCadesSignature(String xml) {
        // CAdES response: SignatureObject/Base64Signature
        String base64 = extractTag(xml, "Base64Signature");
        if (base64 == null || base64.isBlank()) {
            throw new RuntimeException("No Base64Signature found in CAdES eSeal response");
        }
        base64 = base64.replaceAll("\\s+", "");
        return Base64.getDecoder().decode(base64);
    }

    private String extractTag(String xml, String tagName) {
        String open = "<" + tagName + ">";
        int startIdx = xml.indexOf(open);
        if (startIdx >= 0) {
            startIdx += open.length();
        } else {
            String altOpen = "<" + tagName + " ";
            startIdx = xml.indexOf(altOpen);
            if (startIdx >= 0) {
                startIdx = xml.indexOf(">", startIdx) + 1;
            }
        }
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
            String pattern = ":" + tagName + ">";
            int lastIdx = xml.lastIndexOf(pattern);
            if (lastIdx > startIdx) {
                int slashIdx = xml.lastIndexOf("</", lastIdx);
                if (slashIdx >= 0)
                    endIdx = slashIdx;
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
