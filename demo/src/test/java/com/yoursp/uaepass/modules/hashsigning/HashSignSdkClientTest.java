package com.yoursp.uaepass.modules.hashsigning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursp.uaepass.modules.hashsigning.dto.HashStartResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class HashSignSdkClientTest {

    private HashSignSdkClient sdkClient;

    @BeforeEach
    void setUp() {
        sdkClient = new HashSignSdkClient(new ObjectMapper());
        ReflectionTestUtils.setField(sdkClient, "sdkUrl", "http://localhost:9999");
    }

    @Test
    @DisplayName("SDK unavailable → throws HashSignSdkUnavailableException")
    void sdkUnavailableThrows() {
        assertThrows(HashSignSdkUnavailableException.class,
                () -> sdkClient.startProcess("test".getBytes(), "1:[0,0,100,50]"));
    }

    @Test
    @DisplayName("signDocument with unavailable SDK → throws")
    void signDocumentUnavailable() {
        assertThrows(HashSignSdkUnavailableException.class,
                () -> sdkClient.signDocument("txId-1", "sign-id-1", "access-token"));
    }

    @Test
    @DisplayName("HashSigningTxIdReusedException has correct message")
    void txIdReusedExceptionMessage() {
        HashSigningTxIdReusedException ex = new HashSigningTxIdReusedException("test-tx");
        assertTrue(ex.getMessage().contains("test-tx"));
        assertTrue(ex.getMessage().contains("fresh txId"));
    }

    @Test
    @DisplayName("HashSignSdkUnavailableException has correct message")
    void sdkUnavailableExceptionMessage() {
        HashSignSdkUnavailableException ex = new HashSignSdkUnavailableException("down");
        assertEquals("down", ex.getMessage());
    }

    @Test
    @DisplayName("HashStartResult getters work")
    void hashStartResultGetters() {
        HashStartResult result = new HashStartResult("tx1", "sid1", "abcdef");
        assertEquals("tx1", result.getTxId());
        assertEquals("sid1", result.getSignIdentityId());
        assertEquals("abcdef", result.getDigest());
    }
}
