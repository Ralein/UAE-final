package com.yoursp.uaepass.modules.webreg;

import com.yoursp.uaepass.config.UaePassProperties;
import com.yoursp.uaepass.modules.auth.StateService;
import com.yoursp.uaepass.modules.auth.UserSyncService;
import com.yoursp.uaepass.repository.UserSessionRepository;
import com.yoursp.uaepass.service.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WebRegController — focuses on SP_TYPE gating.
 */
@ExtendWith(MockitoExtension.class)
class WebRegControllerTest {

    @Mock
    UaePassProperties uaePassProperties;
    @Mock
    StateService stateService;
    @Mock
    UserSyncService userSyncService;
    @Mock
    UserSessionRepository sessionRepository;
    @Mock
    StringRedisTemplate redisTemplate;
    @Mock
    RestTemplate restTemplate;
    @Mock
    AuditService auditService;

    @InjectMocks
    private WebRegController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(controller, "appBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(controller, "tokenEncryptionKey", "test-key-32-chars-for-encryption!");
    }

    @Test
    @SuppressWarnings("unchecked")
    void registerRejectsGovernmentSP() throws Exception {
        ReflectionTestUtils.setField(controller, "spType", "GOVERNMENT");
        HttpServletResponse response = mock(HttpServletResponse.class);

        ResponseEntity<?> result = controller.register(response);

        assertNotNull(result);
        assertEquals(403, result.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) result.getBody();
        assertNotNull(body);
        assertEquals("NOT_ALLOWED", body.get("error"));
    }

    @Test
    void registerAllowsPrivateSP() throws Exception {
        ReflectionTestUtils.setField(controller, "spType", "PRIVATE");
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(stateService.generateState(eq("WEB_REG"), anyString(), any())).thenReturn("test-state");
        when(uaePassProperties.getAuthorizeUrl()).thenReturn("https://stg-id.uaepass.ae/idshub/authorize");
        when(uaePassProperties.getClientId()).thenReturn("test-client");
        when(uaePassProperties.getScope()).thenReturn("openid");
        when(uaePassProperties.getAcrValues()).thenReturn("urn:safelayer:tws:policies:authentication:level:low");
        when(uaePassProperties.getUiLocales()).thenReturn("en");

        controller.register(response);

        verify(response).sendRedirect(contains("idshub/authorize"));
    }

    @Test
    void registerAllowsBothSP() throws Exception {
        ReflectionTestUtils.setField(controller, "spType", "BOTH");
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(stateService.generateState(eq("WEB_REG"), anyString(), any())).thenReturn("test-state");
        when(uaePassProperties.getAuthorizeUrl()).thenReturn("https://stg-id.uaepass.ae/idshub/authorize");
        when(uaePassProperties.getClientId()).thenReturn("test-client");
        when(uaePassProperties.getScope()).thenReturn("openid");
        when(uaePassProperties.getAcrValues()).thenReturn("urn:safelayer:tws:policies:authentication:level:low");
        when(uaePassProperties.getUiLocales()).thenReturn("en");

        controller.register(response);

        verify(response).sendRedirect(contains("idshub/authorize"));
    }
}
