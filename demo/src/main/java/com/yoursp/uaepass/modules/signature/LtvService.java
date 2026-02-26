package com.yoursp.uaepass.modules.signature;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * LTV (Long-Term Validation) enhancement for signed PDFs.
 * <p>
 * Makes a SOAP call with WS-Security UsernameToken to the UAE PASS LTV web
 * service.
 * The LTV-enhanced PDF includes revocation information embedded in the
 * document,
 * making the signature verifiable even after the signing certificate expires.
 * </p>
 *
 * <h3>Resilience4j Circuit Breaker:</h3>
 * <ul>
 * <li>Opens after 3 consecutive failures</li>
 * <li>Half-open after 30s</li>
 * <li>Graceful degradation: returns original PDF if LTV unavailable</li>
 * </ul>
 */
@Slf4j
@Service
public class LtvService {

    @Value("${signature.ltv-soap-endpoint:}")
    private String ltvSoapEndpoint;

    @Value("${uaepass.client-id:}")
    private String clientId;

    @Value("${uaepass.client-secret:}")
    private String clientSecret;

    /**
     * Apply LTV enhancement to a signed PDF.
     *
     * @param signedPdf the signed PDF bytes
     * @param jobId     the signing job ID (for logging)
     * @return LTV-enhanced PDF bytes, or original PDF if LTV is unavailable
     */
    @CircuitBreaker(name = "ltv", fallbackMethod = "applyLtvFallback")
    public byte[] applyLtv(byte[] signedPdf, UUID jobId) {
        if (ltvSoapEndpoint == null || ltvSoapEndpoint.isBlank()) {
            log.warn("LTV SOAP endpoint not configured — returning original PDF for job {}. " +
                    "Set LTV_SOAP_ENDPOINT env var when available.", jobId);
            return signedPdf;
        }

        log.info("Applying LTV enhancement for job {} via {}", jobId, ltvSoapEndpoint);

        try {
            // Build WS-Security SOAP envelope
            String base64Pdf = java.util.Base64.getEncoder().encodeToString(signedPdf);

            String soapEnvelope = buildLtvSoapEnvelope(base64Pdf);

            // Make SOAP call
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(ltvSoapEndpoint))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"\"")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(soapEnvelope))
                    .build();

            java.net.http.HttpResponse<String> response = httpClient.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("LTV SOAP call returned HTTP " + response.statusCode());
            }

            // Parse LTV-enhanced PDF from SOAP response
            String responseBody = response.body();
            String ltvBase64 = extractBase64FromSoapResponse(responseBody);

            if (ltvBase64 != null) {
                byte[] ltvPdf = java.util.Base64.getDecoder().decode(ltvBase64);
                log.info("LTV enhancement applied for job {} ({} bytes → {} bytes)",
                        jobId, signedPdf.length, ltvPdf.length);
                return ltvPdf;
            } else {
                log.warn("Could not extract LTV PDF from SOAP response for job {}", jobId);
                return signedPdf;
            }
        } catch (Exception e) {
            log.error("LTV enhancement failed for job {}: {}", jobId, e.getMessage());
            throw new RuntimeException("LTV enhancement failed", e);
        }
    }

    private String buildLtvSoapEnvelope(String base64Pdf) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
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
                    <LTVRequest>
                      <Document>%s</Document>
                    </LTVRequest>
                  </soapenv:Body>
                </soapenv:Envelope>
                """
                .formatted(clientId, clientSecret, base64Pdf);
    }

    private String extractBase64FromSoapResponse(String soapResponse) {
        // Simple XML extraction — in production, use proper XML parser
        int start = soapResponse.indexOf("<Document>");
        int end = soapResponse.indexOf("</Document>");
        if (start >= 0 && end > start) {
            return soapResponse.substring(start + "<Document>".length(), end).trim();
        }
        // Try alternative tag names
        start = soapResponse.indexOf("<LTVDocument>");
        end = soapResponse.indexOf("</LTVDocument>");
        if (start >= 0 && end > start) {
            return soapResponse.substring(start + "<LTVDocument>".length(), end).trim();
        }
        return null;
    }

    @SuppressWarnings("unused")
    private byte[] applyLtvFallback(byte[] signedPdf, UUID jobId, Throwable t) {
        log.warn("LTV circuit breaker open for job {} — returning original PDF. Error: {}",
                jobId, t.getMessage());
        return signedPdf;
    }
}
