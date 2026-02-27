package com.yoursp.uaepass.modules.auth;

import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.model.entity.UserSession;
import com.yoursp.uaepass.repository.UserRepository;
import com.yoursp.uaepass.repository.UserSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * MOCK Auth Controller for local development bypassing UAE PASS.
 * This is ONLY active when the "mock" spring profile is used.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@Profile("mock")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class MockAuthController {

    private static final String SESSION_COOKIE_NAME = SessionAuthFilter.SESSION_COOKIE_NAME;
    private static final int SESSION_MAX_AGE = 3600;
    private static final int TOKEN_REDIS_TTL = 3500;
    private static final String TOKEN_KEY_PREFIX = "uaepass_token:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${token.encryption-key}")
    private String tokenEncryptionKey;

    @GetMapping("/dev-login")
    public void devLogin(HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.warn("=== USING MOCK DEV LOGIN BYPASS ===");

        // 1. Get or create a mock user
        Optional<User> existingUser = userRepository.findByUaepassUuid("mock-uuid-1234-5678");
        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
        } else {
            user = User.builder()
                    .uaepassUuid("mock-uuid-1234-5678")
                    .fullNameEn("Dev User")
                    .fullNameAr("ديف يوزر")
                    .email("dev@uaepass.mock")
                    .mobile("971501234567")
                    .userType("SOP2")
                    .nationalityEn("ARE")
                    .idn("784123456789012")
                    .build();
            user = userRepository.save(user);
            log.info("Created new mock user: {}", user.getId());
        }

        // 2. Create a fake access token and store in Redis (some flows require it)
        String fakeAccessToken = "mock.access.token." + SECURE_RANDOM.nextInt(100000);
        String encryptedToken = CryptoUtil.encryptAES256(fakeAccessToken, tokenEncryptionKey);
        redisTemplate.opsForValue().set(
                TOKEN_KEY_PREFIX + user.getId(),
                encryptedToken,
                TOKEN_REDIS_TTL,
                TimeUnit.SECONDS);

        // 3. Create session in DB
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

        // 4. Set httpOnly session cookie
        Cookie cookie = new Cookie(SESSION_COOKIE_NAME, sessionToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(SESSION_MAX_AGE);
        // Note: Removing Secure=true for local dev since it might be HTTP
        if (request.getServerName().equals("localhost")) {
            cookie.setSecure(false);
        } else {
            cookie.setSecure(true);
        }

        response.addCookie(cookie);
        response.setHeader("Set-Cookie", response.getHeader("Set-Cookie") + "; SameSite=Strict");

        log.info("Mock dev login complete. Redirecting to /dashboard");
        response.sendRedirect(frontendUrl + "/dashboard");
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
