package com.yoursp.uaepass.mock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.UUID;

/**
 * Mock SOAP endpoints for local development.
 * <p>
 * Active only when the "mock" Spring profile is enabled.
 * </p>
 * <p>
 * Simulates: eSeal, LTV, Signature Verification SOAP responses.
 * </p>
 *
 * <pre>
 * Run with: SPRING_PROFILES_ACTIVE=mock ./mvnw spring-boot:run
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/mock/soap")
@Profile("mock")
public class MockSoapController {

    // ================================================================
    // eSeal — Mock PAdES/CAdES SOAP response
    // ================================================================

    @PostMapping(value = "/eseal", consumes = MediaType.TEXT_XML_VALUE, produces = MediaType.TEXT_XML_VALUE)
    public String mockEseal(@RequestBody String soapRequest) {
        log.info("[MOCK] eSeal SOAP request received ({} bytes)", soapRequest.length());

        // Return a mock signed PDF (just base64 of "MOCK_SEALED_PDF" bytes)
        String mockBase64 = Base64.getEncoder().encodeToString(
                ("%PDF-1.4 MOCK SEALED DOCUMENT " + UUID.randomUUID()).getBytes());

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <dss:SignResponse xmlns:dss="urn:oasis:names:tc:dss:1.0:core:schema">
                      <dss:Result>
                        <dss:ResultMajor>urn:oasis:names:tc:dss:1.0:resultmajor:Success</dss:ResultMajor>
                      </dss:Result>
                      <dss:SignatureObject>
                        <dss:Base64Data MimeType="application/pdf">%s</dss:Base64Data>
                      </dss:SignatureObject>
                    </dss:SignResponse>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(mockBase64);
    }

    // ================================================================
    // LTV — Mock LTV SOAP response
    // ================================================================

    @PostMapping(value = "/ltv", consumes = MediaType.TEXT_XML_VALUE, produces = MediaType.TEXT_XML_VALUE)
    public String mockLtv(@RequestBody String soapRequest) {
        log.info("[MOCK] LTV SOAP request received ({} bytes)", soapRequest.length());

        String mockBase64 = Base64.getEncoder().encodeToString(
                ("%PDF-1.4 MOCK LTV DOCUMENT " + UUID.randomUUID()).getBytes());

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <dss:VerifyResponse xmlns:dss="urn:oasis:names:tc:dss:1.0:core:schema">
                      <dss:Result>
                        <dss:ResultMajor>urn:oasis:names:tc:dss:1.0:resultmajor:Success</dss:ResultMajor>
                      </dss:Result>
                      <dss:OptionalOutputs>
                        <dss:DocumentWithSignature>
                          <dss:Base64Data MimeType="application/pdf">%s</dss:Base64Data>
                        </dss:DocumentWithSignature>
                      </dss:OptionalOutputs>
                    </dss:VerifyResponse>
                  </soap:Body>
                </soap:Envelope>
                """.formatted(mockBase64);
    }

    // ================================================================
    // Signature Verification — Mock verify SOAP response
    // ================================================================

    @PostMapping(value = "/verify", consumes = MediaType.TEXT_XML_VALUE, produces = MediaType.TEXT_XML_VALUE)
    public String mockVerify(@RequestBody String soapRequest) {
        log.info("[MOCK] Signature Verify SOAP request received ({} bytes)", soapRequest.length());

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <dss:VerifyResponse xmlns:dss="urn:oasis:names:tc:dss:1.0:core:schema">
                      <dss:Result>
                        <dss:ResultMajor>urn:oasis:names:tc:dss:1.0:resultmajor:Success</dss:ResultMajor>
                        <dss:ResultMessage>Signature is valid (MOCK)</dss:ResultMessage>
                      </dss:Result>
                    </dss:VerifyResponse>
                  </soap:Body>
                </soap:Envelope>
                """;
    }
}
