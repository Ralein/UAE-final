package com.yoursp.uaepass.modules.eseal;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Low-level SOAP client for UAE PASS eSeal API.
 * <p>
 * Builds a full SOAP envelope with WS-Security UsernameToken header,
 * sends HTTP POST with Content-Type text/xml, and returns the raw XML response.
 * Protected by a Resilience4j circuit breaker.
 * </p>
 */
@Slf4j
@Component
public class ESealSoapClient {

    @Value("${uaepass.client-id:}")
    private String clientId;

    @Value("${uaepass.client-secret:}")
    private String clientSecret;

    private final HttpClient httpClient;

    public ESealSoapClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Execute a SOAP request against the given endpoint.
     *
     * @param soapEndpoint the SOAP endpoint URL
     * @param soapBody     the SOAP Body content (SignRequest / VerifyRequest XML)
     * @param requestId    the RequestID for logging correlation
     * @return raw XML response string
     */
    @CircuitBreaker(name = "esealCircuitBreaker", fallbackMethod = "executeSoapFallback")
    public String executeSoapRequest(String soapEndpoint, String soapBody, String requestId) {
        String fullEnvelope = buildEnvelope(soapBody);

        log.info("eSeal SOAP request: endpoint={}, requestId={}", soapEndpoint, requestId);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(soapEndpoint))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"\"")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(fullEnvelope))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("eSeal SOAP error: requestId={}, httpStatus={}", requestId, response.statusCode());
                throw new RuntimeException("eSeal SOAP call failed with HTTP " + response.statusCode());
            }

            // Log only ResultMajor — never log base64 document content
            String resultMajor = extractResultMajor(response.body());
            log.info("eSeal SOAP response: requestId={}, resultMajor={}", requestId, resultMajor);

            return response.body();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("eSeal SOAP call interrupted", e);
        } catch (Exception e) {
            log.error("eSeal SOAP call failed: requestId={}, error={}", requestId, e.getMessage());
            throw new RuntimeException("eSeal SOAP call failed", e);
        }
    }

    private String buildEnvelope(String soapBody) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <soapenv:Header>
                    <wsse:Security soapenv:actor="http://schemas.xmlsoap.org/soap/actor/next"
                                   soapenv:mustUnderstand="1"
                                   xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                      <wsse:UsernameToken>
                        <wsse:Username>%s</wsse:Username>
                        <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">%s</wsse:Password>
                      </wsse:UsernameToken>
                    </wsse:Security>
                  </soapenv:Header>
                  <soapenv:Body>
                    %s
                  </soapenv:Body>
                </soapenv:Envelope>
                """
                .formatted(clientId, clientSecret, soapBody);
    }

    private String extractResultMajor(String xml) {
        String tag = "ResultMajor";
        int start = xml.indexOf("<" + tag + ">");
        if (start < 0) {
            start = xml.indexOf("<" + tag + " ");
            if (start >= 0)
                start = xml.indexOf(">", start) + 1;
        } else {
            start += tag.length() + 2;
        }
        int end = xml.indexOf("</" + tag + ">");
        if (start >= 0 && end > start) {
            return xml.substring(start, end).trim();
        }
        return "UNKNOWN";
    }

    @SuppressWarnings("unused")
    private String executeSoapFallback(String soapEndpoint, String soapBody,
            String requestId, Throwable t) {
        log.error("eSeal circuit breaker open: requestId={}, error={}", requestId, t.getMessage());
        throw new ESealUnavailableException("eSeal service is temporarily unavailable. Please try again later.");
    }
}
