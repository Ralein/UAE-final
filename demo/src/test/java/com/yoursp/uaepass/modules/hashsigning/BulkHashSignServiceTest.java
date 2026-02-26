package com.yoursp.uaepass.modules.hashsigning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BulkHashSignServiceTest {

    @Test
    @DisplayName("computeDigestsSummary: SHA-256 of concatenated digest byte arrays")
    void computeDigestsSummarySingle() {
        // Single digest → SHA-256(digest_bytes) should equal
        // SHA-256(hex_to_bytes(digest))
        byte[] digest1 = BulkHashSignService
                .hexToBytes("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
        String summary = BulkHashSignService.computeDigestsSummary(List.of(digest1));

        assertNotNull(summary);
        assertEquals(64, summary.length()); // 32 bytes as hex = 64 chars
    }

    @Test
    @DisplayName("computeDigestsSummary: two digests concatenated then hashed")
    void computeDigestsSummaryMultiple() throws Exception {
        byte[] digest1 = BulkHashSignService.hexToBytes("aaaa");
        byte[] digest2 = BulkHashSignService.hexToBytes("bbbb");

        String summary = BulkHashSignService.computeDigestsSummary(List.of(digest1, digest2));

        // Manually compute expected: SHA-256(concat(0xAAAA, 0xBBBB))
        byte[] combined = new byte[4];
        System.arraycopy(digest1, 0, combined, 0, 2);
        System.arraycopy(digest2, 0, combined, 2, 2);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] expected = sha256.digest(combined);
        StringBuilder sb = new StringBuilder();
        for (byte b : expected)
            sb.append(String.format("%02x", b));

        assertEquals(sb.toString(), summary);
    }

    @Test
    @DisplayName("computeDigestsSummary: order matters")
    void digestsSummaryOrderMatters() {
        byte[] d1 = BulkHashSignService.hexToBytes("aabb");
        byte[] d2 = BulkHashSignService.hexToBytes("ccdd");

        String summary12 = BulkHashSignService.computeDigestsSummary(List.of(d1, d2));
        String summary21 = BulkHashSignService.computeDigestsSummary(List.of(d2, d1));

        assertNotEquals(summary12, summary21, "Order of digests must affect the summary");
    }

    @Test
    @DisplayName("hexToBytes correctly converts hex strings")
    void hexToBytesCorrect() {
        byte[] result = BulkHashSignService.hexToBytes("deadbeef");
        assertEquals(4, result.length);
        assertEquals((byte) 0xDE, result[0]);
        assertEquals((byte) 0xAD, result[1]);
        assertEquals((byte) 0xBE, result[2]);
        assertEquals((byte) 0xEF, result[3]);
    }
}
