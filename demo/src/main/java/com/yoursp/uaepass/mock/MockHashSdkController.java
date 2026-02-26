package com.yoursp.uaepass.mock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Mock Hash Signing SDK controller for local development.
 * <p>
 * Active only when the "mock" Spring profile is enabled.
 * </p>
 * <p>
 * Simulates the Hash Signing Docker sidecar (normally on port 8081).
 * </p>
 *
 * <pre>
 * Run with: SPRING_PROFILES_ACTIVE=mock ./mvnw spring-boot:run
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/mock/hashsdk")
@Profile("mock")
public class MockHashSdkController {

    // ================================================================
    // POST /start — Start hash signing process
    // ================================================================

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody Map<String, Object> request) {
        log.info("[MOCK] Hash SDK /start called: txId={}", request.get("txId"));

        String txId = (String) request.getOrDefault("txId", UUID.randomUUID().toString());

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "txId", txId,
                "authorizeUrl", "https://stg-id.uaepass.ae/idshub/authorize?mock=true&txId=" + txId,
                "message", "MOCK: Hash signing process started"));
    }

    // ================================================================
    // POST /finalize — Finalize hash signing (after user approval)
    // ================================================================

    @PostMapping("/finalize")
    public ResponseEntity<?> finalize(@RequestBody Map<String, Object> request) {
        log.info("[MOCK] Hash SDK /finalize called: txId={}", request.get("txId"));

        // Return mock PKCS#7 signed hash (base64-encoded random bytes)
        byte[] mockSignature = new byte[256];
        new Random().nextBytes(mockSignature);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "txId", request.getOrDefault("txId", "mock-tx"),
                "signedHash", Base64.getEncoder().encodeToString(mockSignature),
                "message", "MOCK: Hash signed successfully"));
    }

    // ================================================================
    // GET /status — Check signing status
    // ================================================================

    @GetMapping("/status/{txId}")
    public ResponseEntity<?> status(@PathVariable String txId) {
        log.info("[MOCK] Hash SDK /status called: txId={}", txId);

        return ResponseEntity.ok(Map.of(
                "status", "COMPLETED",
                "txId", txId,
                "message", "MOCK: Signing completed"));
    }
}
