package com.yoursp.uaepass.modules.hashsigning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursp.uaepass.modules.hashsigning.dto.HashStartResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP client for the UAE PASS Hash Signing Docker SDK sidecar.
 * <p>
 * The SDK must be running on the configured URL (default:
 * http://localhost:8081).
 * </p>
 */
@Slf4j
@Component
public class HashSignSdkClient {

    @Value("${hashsign.sdk-url:http://localhost:8081}")
    private String sdkUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HashSignSdkClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Step 1 — Start the hash signing process.
     * <p>
     * Sends the PDF to the SDK which prepares it with a ByteRange reservation
     * and returns the SHA-256 digest of the prepared document.
     * </p>
     *
     * @param pdfBytes the raw PDF bytes
     * @param signProp signature position: "{page}:[{x},{y},{width},{height}]"
     * @return HashStartResult with txId, signIdentityId, and digest
     */
    public HashStartResult startProcess(byte[] pdfBytes, String signProp) {
        String url = sdkUrl + "/start";
        log.info("Hash SDK /start: url={}, pdfSize={}, signProp={}", url, pdfBytes.length, signProp);

        try {
            // Build multipart-like JSON request with base64 PDF
            String base64Pdf = java.util.Base64.getEncoder().encodeToString(pdfBytes);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "document", base64Pdf,
                    "signProp", signProp));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 412) {
                throw new HashSigningTxIdReusedException("(from /start)");
            }

            if (response.statusCode() != 200) {
                throw new RuntimeException("Hash SDK /start returned HTTP " + response.statusCode());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);

            HashStartResult result = new HashStartResult();
            result.setTxId((String) body.get("txId"));
            result.setSignIdentityId((String) body.get("sign_identity_id"));
            result.setDigest((String) body.get("digest"));

            log.info("Hash SDK /start success: txId={}, signIdentityId={}",
                    result.getTxId(), result.getSignIdentityId());

            return result;

        } catch (HashSigningTxIdReusedException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            throw new HashSignSdkUnavailableException(
                    "Hash Signing SDK is not reachable at " + sdkUrl, e);
        } catch (Exception e) {
            log.error("Hash SDK /start failed: {}", e.getMessage());
            throw new HashSignSdkUnavailableException("Hash SDK /start call failed", e);
        }
    }

    /**
     * Step 3 — Sign the prepared document.
     * <p>
     * Sends the access token (obtained from UAE PASS auth) to the SDK
     * which uses it to sign the hash and embed the PKCS#7 signature.
     * </p>
     *
     * @param txId           transaction ID from /start
     * @param signIdentityId sign identity from /start
     * @param accessToken    access token from OAuth code exchange
     * @return final signed PDF bytes
     */
    public byte[] signDocument(String txId, String signIdentityId, String accessToken) {
        String url = sdkUrl + "/sign";
        log.info("Hash SDK /sign: txId={}", txId);

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "txId", txId,
                    "sign_identity_id", signIdentityId));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("X-SIGN-ACCESSTOKEN", accessToken)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 412) {
                throw new HashSigningTxIdReusedException(txId);
            }

            if (response.statusCode() != 200) {
                throw new RuntimeException("Hash SDK /sign returned HTTP " + response.statusCode());
            }

            byte[] signedPdf = response.body();
            log.info("Hash SDK /sign success: txId={}, signedSize={} bytes", txId, signedPdf.length);

            return signedPdf;

        } catch (HashSigningTxIdReusedException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            throw new HashSignSdkUnavailableException(
                    "Hash Signing SDK is not reachable at " + sdkUrl, e);
        } catch (Exception e) {
            log.error("Hash SDK /sign failed: txId={}, error={}", txId, e.getMessage());
            throw new HashSignSdkUnavailableException("Hash SDK /sign call failed", e);
        }
    }
}
