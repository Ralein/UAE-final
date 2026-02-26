package com.yoursp.uaepass.modules.signature;

import com.yoursp.uaepass.modules.signature.dto.VerificationResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * Verifies digital signatures on signed PDFs via UAE PASS SOAP Verification
 * API.
 * <p>
 * Uses WS-Security UsernameToken authentication.
 * Profile: {@code urn:safelayer:tws:dss:1.0:profiles:pdf:1.0:verify}
 * </p>
 */
@Slf4j
@Service
public class SignatureVerificationService {

    @Value("${signature.verify-soap-endpoint:}")
    private String verifySoapEndpoint;

    @Value("${uaepass.client-id:}")
    private String clientId;

    @Value("${uaepass.client-secret:}")
    private String clientSecret;

    /**
     * Verify signatures in a signed PDF.
     *
     * @param signedPdf the signed PDF bytes
     * @return verification result with signer identity and validity
     */
    @CircuitBreaker(name = "signVerify", fallbackMethod = "verifyFallback")
    public VerificationResult verifySignature(byte[] signedPdf) {
        if (verifySoapEndpoint == null || verifySoapEndpoint.isBlank()) {
            log.warn("Verification SOAP endpoint not configured — returning unverified result");
            return VerificationResult.builder()
                    .valid(false)
                    .resultMajor("NotConfigured")
                    .resultMinor("SIGNATURE_VERIFY_SOAP_ENDPOINT not set")
                    .build();
        }

        log.info("Verifying signature via {}", verifySoapEndpoint);

        try {
            String base64Pdf = Base64.getEncoder().encodeToString(signedPdf);
            String soapEnvelope = buildVerifySoapEnvelope(base64Pdf);

            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(verifySoapEndpoint))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"\"")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(soapEnvelope))
                    .build();

            java.net.http.HttpResponse<String> response = httpClient.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return VerificationResult.builder()
                        .valid(false)
                        .resultMajor("Error")
                        .resultMinor("HTTP " + response.statusCode())
                        .build();
            }

            return parseVerifyResponse(response.body());

        } catch (Exception e) {
            log.error("Signature verification failed: {}", e.getMessage());
            throw new RuntimeException("Signature verification failed", e);
        }
    }

    private String buildVerifySoapEnvelope(String base64Pdf) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:dss="urn:oasis:names:tc:dss:1.0:core:schema"
                                  xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                  <soapenv:Header>
                    <wsse:Security>
                      <wsse:UsernameToken>
                        <wsse:Username>%s</wsse:Username>
                        <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">%s</wsse:Password>
                      </wsse:UsernameToken>
                    </wsse:Security>
                  </soapenv:Header>
                  <soapenv:Body>
                    <dss:VerifyRequest Profile="urn:safelayer:tws:dss:1.0:profiles:pdf:1.0:verify">
                      <dss:InputDocuments>
                        <dss:Document>
                          <dss:Base64Data MimeType="application/pdf">%s</dss:Base64Data>
                        </dss:Document>
                      </dss:InputDocuments>
                    </dss:VerifyRequest>
                  </soapenv:Body>
                </soapenv:Envelope>
                """
                .formatted(clientId, clientSecret, base64Pdf);
    }

    private VerificationResult parseVerifyResponse(String soapResponse) {
        // Extract ResultMajor
        String resultMajor = extractTag(soapResponse, "ResultMajor");
        String resultMinor = extractTag(soapResponse, "ResultMinor");
        String signerName = extractTag(soapResponse, "SignerIdentity");
        String signingTime = extractTag(soapResponse, "SigningTime");

        boolean valid = resultMajor != null && resultMajor.contains("Success");

        return VerificationResult.builder()
                .valid(valid)
                .resultMajor(resultMajor)
                .resultMinor(resultMinor)
                .signerName(signerName)
                .signingTime(signingTime)
                .build();
    }

    private String extractTag(String xml, String tagName) {
        String open = "<" + tagName + ">";
        String altOpen = "<" + tagName + " ";
        String close = "</" + tagName + ">";

        int startIdx = xml.indexOf(open);
        if (startIdx < 0) {
            startIdx = xml.indexOf(altOpen);
            if (startIdx >= 0) {
                startIdx = xml.indexOf(">", startIdx) + 1;
            }
        } else {
            startIdx += open.length();
        }

        int endIdx = xml.indexOf(close);
        if (startIdx >= 0 && endIdx > startIdx) {
            return xml.substring(startIdx, endIdx).trim();
        }
        return null;
    }

    @SuppressWarnings("unused")
    private VerificationResult verifyFallback(byte[] signedPdf, Throwable t) {
        log.warn("Verification circuit breaker open: {}", t.getMessage());
        return VerificationResult.builder()
                .valid(false)
                .resultMajor("ServiceUnavailable")
                .resultMinor(t.getMessage())
                .build();
    }
}
