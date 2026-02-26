package com.yoursp.uaepass.modules.eseal;

import com.yoursp.uaepass.modules.eseal.dto.ESealVerifyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Verifies PAdES and CAdES eSeal'd documents via UAE PASS SOAP Verification
 * API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ESealVerificationService {

    private final ESealSoapClient soapClient;

    @Value("${eseal.soap-endpoint:}")
    private String soapEndpoint;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Verify a PAdES-sealed PDF document.
     */
    public ESealVerifyResult verifyPadesESeal(byte[] sealedPdf) {
        if (soapEndpoint == null || soapEndpoint.isBlank()) {
            return ESealVerifyResult.builder()
                    .valid(false)
                    .resultMajor("NotConfigured")
                    .resultMessage("ESEAL_SOAP_ENDPOINT not configured")
                    .build();
        }

        String requestId = generateRequestId();
        String base64Pdf = Base64.getEncoder().encodeToString(sealedPdf);

        String verifyBody = """
                <VerifyRequest xmlns="http://www.docs.oasis-open.org/dss/2004/06/oasis-dss-1.0-core-schema-wd-27.xsd"
                               Profile="urn:safelayer:tws:dss:1.0:profiles:pdf:1.0:verify"
                               RequestID="%s">
                  <InputDocuments>
                    <Document>
                      <Base64Data MimeType="application/pdf">%s</Base64Data>
                    </Document>
                  </InputDocuments>
                </VerifyRequest>
                """.formatted(requestId, base64Pdf);

        return executeVerify(verifyBody, requestId);
    }

    /**
     * Verify a CAdES-sealed document with its detached PKCS#7 signature.
     */
    public ESealVerifyResult verifyCadesESeal(byte[] document, byte[] signature) {
        if (soapEndpoint == null || soapEndpoint.isBlank()) {
            return ESealVerifyResult.builder()
                    .valid(false)
                    .resultMajor("NotConfigured")
                    .resultMessage("ESEAL_SOAP_ENDPOINT not configured")
                    .build();
        }

        String requestId = generateRequestId();
        String base64Doc = Base64.getEncoder().encodeToString(document);
        String base64Sig = Base64.getEncoder().encodeToString(signature);

        String verifyBody = """
                <VerifyRequest xmlns="http://www.docs.oasis-open.org/dss/2004/06/oasis-dss-1.0-core-schema-wd-27.xsd"
                               Profile="urn:safelayer:tws:dss:1.0:profiles:cmspkcs7sig:1.0:verify"
                               RequestID="%s">
                  <InputDocuments>
                    <Document>
                      <Base64Data>%s</Base64Data>
                    </Document>
                  </InputDocuments>
                  <SignatureObject>
                    <Base64Signature>%s</Base64Signature>
                  </SignatureObject>
                </VerifyRequest>
                """.formatted(requestId, base64Doc, base64Sig);

        return executeVerify(verifyBody, requestId);
    }

    private ESealVerifyResult executeVerify(String verifyBody, String requestId) {
        try {
            String responseXml = soapClient.executeSoapRequest(soapEndpoint, verifyBody, requestId);

            String resultMajor = extractTag(responseXml, "ResultMajor");
            String resultMinor = extractTag(responseXml, "ResultMinor");
            String signerName = extractTag(responseXml, "SignerIdentity");
            String signingTime = extractTag(responseXml, "SigningTime");

            boolean valid = resultMajor != null && resultMajor.contains("Success");
            String message = ESealErrorCodeMapper.toMessage(resultMinor);

            log.info("eSeal verification: requestId={}, resultMajor={}, valid={}", requestId, resultMajor, valid);

            return ESealVerifyResult.builder()
                    .valid(valid)
                    .resultMajor(resultMajor)
                    .resultMinor(resultMinor)
                    .resultMessage(message)
                    .signerName(signerName)
                    .signingTime(signingTime)
                    .build();

        } catch (ESealUnavailableException e) {
            return ESealVerifyResult.builder()
                    .valid(false)
                    .resultMajor("ServiceUnavailable")
                    .resultMessage(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("eSeal verification failed: requestId={}, error={}", requestId, e.getMessage());
            return ESealVerifyResult.builder()
                    .valid(false)
                    .resultMajor("Error")
                    .resultMessage(e.getMessage())
                    .build();
        }
    }

    private String extractTag(String xml, String tagName) {
        String open = "<" + tagName + ">";
        int startIdx = xml.indexOf(open);
        if (startIdx >= 0) {
            startIdx += open.length();
        } else {
            String altOpen = "<" + tagName + " ";
            startIdx = xml.indexOf(altOpen);
            if (startIdx >= 0)
                startIdx = xml.indexOf(">", startIdx) + 1;
        }
        if (startIdx < 0) {
            int nsIdx = xml.indexOf(":" + tagName + ">");
            if (nsIdx >= 0)
                startIdx = nsIdx + tagName.length() + 2;
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
        for (byte b : bytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
