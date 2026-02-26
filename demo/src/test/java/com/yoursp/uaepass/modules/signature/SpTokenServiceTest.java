package com.yoursp.uaepass.modules.signature;

import com.yoursp.uaepass.config.UaePassProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class SpTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private UaePassProperties uaePassProperties;

    private SpTokenService spTokenService;

    @BeforeEach
    void setUp() {
        // Must create manually since constructor needs WebClient.Builder
        WebClient.Builder builder = WebClient.builder();
        spTokenService = new SpTokenService(builder, redisTemplate, uaePassProperties);
        ReflectionTestUtils.setField(spTokenService, "spTokenUrl",
                "https://stg-id.uaepass.ae/trustedx-authserver/oauth/main-as/token");
        ReflectionTestUtils.setField(spTokenService, "signScope",
                "urn:safelayer:eidas:sign:process:document");
    }

    @Test
    @DisplayName("Returns cached token if available in Redis")
    void returnsCachedToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("sp_sign_token")).thenReturn("cached-sp-token-12345678");

        String token = spTokenService.getSpAccessToken();

        assertEquals("cached-sp-token-12345678", token);
    }

    @Test
    @DisplayName("Cache miss → attempts HTTP call (will fail without real server)")
    void noCachedToken_attemptsHttpCall() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("sp_sign_token")).thenReturn(null);
        when(uaePassProperties.getClientId()).thenReturn("test-client");
        when(uaePassProperties.getClientSecret()).thenReturn("test-secret");

        // WebClient will fail since there's no real server — validates cache-first
        // logic
        assertThrows(Exception.class, () -> spTokenService.getSpAccessToken());
    }
}
