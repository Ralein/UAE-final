package com.yoursp.uaepass.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LogMaskingConverter — ensures sensitive data is masked.
 */
class LogMaskingConverterTest {

    private final LogMaskingConverter converter = new LogMaskingConverter();

    @Test
    void masksBearerToken() {
        String input = "Authorization: Bearer a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6";
        String result = converter.transform(null, input);
        assertTrue(result.contains("Bearer a1b2c3d4..."));
        assertFalse(result.contains("i9j0k1l2m3n4o5p6"));
    }

    @Test
    void masksAccessTokenValue() {
        String input = "access_token=a1b2c3d4e5f6g7h8i9j0k1l2m3n4";
        String result = converter.transform(null, input);
        assertTrue(result.contains("a1b2c3d4..."));
        assertFalse(result.contains("i9j0k1l2m3n4"));
    }

    @Test
    void masksClientSecret() {
        String input = "client_secret=mysupersecretvalue123";
        String result = converter.transform(null, input);
        assertTrue(result.contains("[REDACTED]"));
        assertFalse(result.contains("mysupersecretvalue123"));
    }

    @Test
    void masksEmiratesId() {
        String input = "User IDN: 784-1990-1234567-1";
        String result = converter.transform(null, input);
        assertTrue(result.contains("EID:[REDACTED]"));
        assertFalse(result.contains("784-1990-1234567-1"));
    }

    @Test
    void masksMobileNumber() {
        String input = "Mobile: +971501234567";
        String result = converter.transform(null, input);
        assertTrue(result.contains("***4567"));
        assertFalse(result.contains("+971501234567"));
    }

    @Test
    void passesNonSensitiveDataThrough() {
        String input = "User logged in from IP 10.0.0.1";
        String result = converter.transform(null, input);
        assertEquals(input, result);
    }

    @Test
    void handlesNullInput() {
        assertNull(converter.transform(null, null));
    }
}
