package com.yoursp.uaepass.modules.webreg;

import com.yoursp.uaepass.config.UaePassProperties;
import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.model.entity.UserSession;
import com.yoursp.uaepass.modules.auth.CryptoUtil;
import com.yoursp.uaepass.modules.auth.SessionAuthFilter;
import com.yoursp.uaepass.modules.auth.StateService;
import com.yoursp.uaepass.modules.auth.UserSyncService;
import com.yoursp.uaepass.modules.auth.dto.StatePayload;
import com.yoursp.uaepass.modules.auth.exception.InvalidStateException;
import com.yoursp.uaepass.repository.UserSessionRepository;
import com.yoursp.uaepass.service.AuditService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Web Registration controller — allows new users to create a UAE PASS account
 * from within the SP app. <b>Private Organizations only.</b>
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@SuppressWarnings({ "null", "rawtypes", "unchecked" })
public class WebRegController {

    private static final String SESSION_COOKIE_NAME = SessionAuthFilter.SESSION_COOKIE_NAME;
    private static final int SESSION_MAX_AGE = 3600;
    private static final int TOKEN_REDIS_TTL = 3500;
    private static final String TOKEN_KEY_PREFIX = "uaepass_token:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UaePassProperties uaePassProperties;
    private final StateService stateService;
    private final UserSyncService userSyncService;
    private final UserSessionRepository sessionRepository;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final AuditService auditService;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Value("${token.encryption-key}")
    private String tokenEncryptionKey;

    @Value("${sp.type:PRIVATE}")
    private String spType;

    // ================================================================
    // GET /auth/register
    // ================================================================

    @GetMapping("/register")
    public ResponseEntity<?> register(HttpServletResponse response) throws Exception {
        // Gate: Private Organizations only (PRIVATE or BOTH allowed)
        if ("GOVERNMENT".equalsIgnoreCase(spType)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "NOT_ALLOWED",
                            "message", "Web Registration is only available for Private Organizations"));
        }

        String state = stateService.generateState("WEB_REG", "/welcome", null);

        String authorizeUrl = UriComponentsBuilder
                .fromHttpUrl(uaePassProperties.getAuthorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", uaePassProperties.getClientId())
                .queryParam("redirect_uri", appBaseUrl + "/auth/register/callback")
                .queryParam("scope", uaePassProperties.getScope())
                .queryParam("state", state)
                .queryParam("acr_values", uaePassProperties.getAcrValues())
                .queryParam("ui_locales", uaePassProperties.getUiLocales())
                .build()
                .toUriString();

        log.info("Redirecting to UAE PASS web registration (state={})", state);
        response.sendRedirect(authorizeUrl);
        return null;
    }

    // ================================================================
    // GET /auth/register/callback
    // ================================================================

    @GetMapping("/register/callback")
    public void registerCallback(@RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDesc,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        if (error != null) {
            log.warn("Web registration error: {} — {}", error, errorDesc);
            response.sendRedirect(frontendUrl + "/auth/error?error=" + error);
            return;
        }

        // Consume state
        StatePayload statePayload;
        try {
            statePayload = stateService.consumeState(state);
        } catch (InvalidStateException ex) {
            log.warn("Invalid state during register callback: {}", ex.getMessage());
            response.sendRedirect(frontendUrl + "/auth/error?error=invalid_state");
            return;
        }

        if (!"WEB_REG".equals(statePayload.flowType())) {
            response.sendRedirect(frontendUrl + "/auth/error?error=invalid_flow");
            return;
        }

        // Exchange code → tokens
        Map<String, Object> tokenResponse = exchangeCodeForTokens(code);
        String accessToken = (String) tokenResponse.get("access_token");

        if (accessToken == null) {
            log.error("No access_token in register callback token response");
            response.sendRedirect(frontendUrl + "/auth/error?error=token_exchange_failed");
            return;
        }

        // Fetch user info
        Map<String, Object> userInfo = fetchUserInfo(accessToken);

        // Sync user — this is a brand-new UAE PASS user
        User user = userSyncService.syncUser(userInfo);

        // Audit — mark as web registration
        auditService.log(user.getId(), "WEB_REGISTRATION", "User",
                user.getId().toString(), getClientIp(request),
                Map.of("source", "web_registration", "userType",
                        user.getUserType() != null ? user.getUserType() : "unknown"));

        // Store encrypted access token in Redis
        String encryptedToken = CryptoUtil.encryptAES256(accessToken, tokenEncryptionKey);
        redisTemplate.opsForValue().set(
                TOKEN_KEY_PREFIX + user.getId(),
                encryptedToken,
                TOKEN_REDIS_TTL,
                TimeUnit.SECONDS);

        // Create session
        String sessionToken = generateSessionToken();
        UserSession session = UserSession.builder()
                .userId(user.getId())
                .sessionToken(sessionToken)
                .uaepassTokenRef(TOKEN_KEY_PREFIX + user.getId())
                .tokenExpires(OffsetDateTime.now().plusSeconds(SESSION_MAX_AGE))
                .ipAddress(getClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .build();
        sessionRepository.save(session);

        // Set httpOnly session cookie
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, sessionToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(SESSION_MAX_AGE);
        response.addCookie(cookie);
        response.setHeader("Set-Cookie",
                response.getHeader("Set-Cookie") + "; SameSite=Strict");

        // Redirect to welcome/onboarding page
        log.info("Web registration complete for user {} — redirecting to welcome", user.getId());
        response.sendRedirect(frontendUrl + "/welcome");
    }

    // ================================================================
    // Helpers (same as AuthController)
    // ================================================================

    private Map<String, Object> exchangeCodeForTokens(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(uaePassProperties.getClientId(), uaePassProperties.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", appBaseUrl + "/auth/register/callback");

        ResponseEntity<Map> resp = restTemplate.exchange(
                uaePassProperties.getTokenUrl(),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        return resp.getBody();
    }

    private Map<String, Object> fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map> resp = restTemplate.exchange(
                uaePassProperties.getUserInfoUrl(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        return resp.getBody();
    }

    private String generateSessionToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
