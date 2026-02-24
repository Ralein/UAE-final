package com.yoursp.uaepass.modules.auth;

import com.yoursp.uaepass.config.UaePassProperties;
import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.model.entity.UserSession;
import com.yoursp.uaepass.modules.auth.dto.StatePayload;
import com.yoursp.uaepass.modules.auth.dto.TokenIntrospectResponse;
import com.yoursp.uaepass.modules.auth.dto.UserProfileDto;
import com.yoursp.uaepass.modules.auth.exception.InvalidStateException;
import com.yoursp.uaepass.repository.UserSessionRepository;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * UAE PASS OAuth2 / OIDC authentication controller.
 * <p>
 * Handles the complete authorization code flow:
 * /auth/login → redirect to UAE PASS → /auth/callback → session cookie
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String SESSION_COOKIE_NAME = SessionAuthFilter.SESSION_COOKIE_NAME;
    private static final int SESSION_MAX_AGE = 3600; // 1 hour
    private static final int TOKEN_REDIS_TTL = 3500; // slightly less than access token TTL
    private static final String TOKEN_KEY_PREFIX = "uaepass_token:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UaePassProperties uaePassProperties;
    private final StateService stateService;
    private final UserSyncService userSyncService;
    private final UserSessionRepository sessionRepository;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${token.encryption-key}")
    private String tokenEncryptionKey;

    // ================================================================
    // GET /auth/login — Redirect to UAE PASS authorization
    // ================================================================

    @GetMapping("/login")
    public void login(@RequestParam(value = "redirectAfter", defaultValue = "/dashboard") String redirectAfter,
            HttpServletResponse response) throws Exception {

        String state = stateService.generateState("AUTH", redirectAfter, null);

        String authorizeUrl = UriComponentsBuilder.fromHttpUrl(uaePassProperties.getAuthorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", uaePassProperties.getClientId())
                .queryParam("redirect_uri", uaePassProperties.getRedirectUri())
                .queryParam("scope", uaePassProperties.getScope())
                .queryParam("state", state)
                .queryParam("acr_values", uaePassProperties.getAcrValues())
                .queryParam("ui_locales", uaePassProperties.getUiLocales())
                .build()
                .toUriString();

        log.info("Redirecting to UAE PASS authorize (state={})", state);
        response.sendRedirect(authorizeUrl);
    }

    // ================================================================
    // GET /auth/callback — Process authorization code
    // ================================================================

    @GetMapping("/callback")
    public void callback(@RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDesc,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        // 1. Check for error from UAE PASS
        if (error != null) {
            log.warn("UAE PASS auth error: {} — {}", error, errorDesc);
            response.sendRedirect(frontendUrl + "/auth/error?error=" + error);
            return;
        }

        // 2. Consume state (atomic — throws InvalidStateException if
        // invalid/expired/reused)
        StatePayload statePayload;
        try {
            statePayload = stateService.consumeState(state);
        } catch (InvalidStateException ex) {
            log.warn("Invalid state during callback: {}", ex.getMessage());
            response.sendRedirect(frontendUrl + "/auth/error?error=invalid_state");
            return;
        }

        // 3. Exchange code for tokens
        Map<String, Object> tokenResponse = exchangeCodeForTokens(code);
        String accessToken = (String) tokenResponse.get("access_token");
        // id_token validation would go here in production (JWKS check)
        // For now we proceed with the access_token

        if (accessToken == null) {
            log.error("No access_token in token response");
            response.sendRedirect(frontendUrl + "/auth/error?error=token_exchange_failed");
            return;
        }

        // 4. Fetch user info
        Map<String, Object> userInfo = fetchUserInfo(accessToken);

        // 5. Sync user to DB
        User user = userSyncService.syncUser(userInfo);

        // 6. Store encrypted access token in Redis
        String encryptedToken = CryptoUtil.encryptAES256(accessToken, tokenEncryptionKey);
        redisTemplate.opsForValue().set(
                TOKEN_KEY_PREFIX + user.getId(),
                encryptedToken,
                TOKEN_REDIS_TTL,
                TimeUnit.SECONDS);

        // 7. Create session
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

        // 8. Set httpOnly session cookie
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, sessionToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(SESSION_MAX_AGE);
        // SameSite=Strict is set via response header since Cookie API doesn't support
        // it
        response.addCookie(cookie);
        response.setHeader("Set-Cookie",
                response.getHeader("Set-Cookie") + "; SameSite=Strict");

        // 9. Redirect to frontend
        String redirectTarget = statePayload.redirectAfter();
        if (redirectTarget == null || redirectTarget.isBlank()) {
            redirectTarget = "/dashboard";
        }
        // If redirectTarget is a relative path, prepend frontend URL
        if (!redirectTarget.startsWith("http")) {
            redirectTarget = frontendUrl + redirectTarget;
        }

        log.info("Auth callback complete for user {} — redirecting to {}", user.getId(), redirectTarget);
        response.sendRedirect(redirectTarget);
    }

    // ================================================================
    // POST /auth/logout — Clear session and redirect to UAE PASS logout
    // ================================================================

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String sessionToken = extractSessionCookie(request);

        if (sessionToken != null) {
            sessionRepository.findBySessionToken(sessionToken).ifPresent(session -> {
                // Delete token from Redis
                redisTemplate.delete(TOKEN_KEY_PREFIX + session.getUserId());
                // Delete session from DB
                sessionRepository.delete(session);
                log.info("Logged out user {}", session.getUserId());
            });
        }

        // Clear cookie
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        // Redirect to UAE PASS logout
        String logoutUrl = uaePassProperties.getLogoutUrl()
                + "?post_logout_redirect_uri=" + frontendUrl;
        response.sendRedirect(logoutUrl);
    }

    // ================================================================
    // GET /auth/me — Return current user profile
    // ================================================================

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> me(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserProfileDto dto = UserProfileDto.builder()
                .id(user.getId())
                .fullNameEn(user.getFullNameEn())
                .fullNameAr(user.getFullNameAr())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .userType(user.getUserType())
                .nationality(user.getNationalityEn())
                .linkedAt(user.getLinkedAt())
                .build();

        return ResponseEntity.ok(dto);
    }

    // ================================================================
    // POST /auth/validate-token — Introspect UAE PASS token
    // ================================================================

    @PostMapping("/validate-token")
    public ResponseEntity<TokenIntrospectResponse> validateToken(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Retrieve encrypted token from Redis
        String encryptedToken = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + user.getId());
        if (encryptedToken == null) {
            TokenIntrospectResponse inactive = new TokenIntrospectResponse();
            inactive.setActive(false);
            return ResponseEntity.ok(inactive);
        }

        String accessToken = CryptoUtil.decryptAES256(encryptedToken, tokenEncryptionKey);

        // Call UAE PASS introspect endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(uaePassProperties.getClientId(), uaePassProperties.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", accessToken);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<TokenIntrospectResponse> introspectResponse = restTemplate.exchange(
                uaePassProperties.getIntrospectUrl(),
                HttpMethod.POST,
                entity,
                TokenIntrospectResponse.class);

        TokenIntrospectResponse result = introspectResponse.getBody();

        // Verify client_id matches ours
        if (result != null && result.isActive()) {
            if (!uaePassProperties.getClientId().equals(result.getClientId())) {
                log.warn("Token introspection client_id mismatch: expected={}, got={}",
                        uaePassProperties.getClientId(), result.getClientId());
                result.setActive(false);
            }
        }

        return ResponseEntity.ok(result);
    }

    // ================================================================
    // Private helpers
    // ================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeCodeForTokens(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(uaePassProperties.getClientId(), uaePassProperties.getClientSecret());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", uaePassProperties.getRedirectUri());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        log.debug("Exchanging auth code for tokens at {}", uaePassProperties.getTokenUrl());

        ResponseEntity<Map> response = restTemplate.exchange(
                uaePassProperties.getTokenUrl(),
                HttpMethod.POST,
                entity,
                Map.class);

        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        log.debug("Fetching user info from {}", uaePassProperties.getUserInfoUrl());

        ResponseEntity<Map> response = restTemplate.exchange(
                uaePassProperties.getUserInfoUrl(),
                HttpMethod.GET,
                entity,
                Map.class);

        return response.getBody();
    }

    private String generateSessionToken() {
        byte[] bytes = new byte[64];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String extractSessionCookie(HttpServletRequest request) {
        if (request.getCookies() == null)
            return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> SESSION_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
