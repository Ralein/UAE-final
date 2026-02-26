package com.yoursp.uaepass.modules.auth;

import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.model.entity.UserSession;
import com.yoursp.uaepass.repository.UserRepository;
import com.yoursp.uaepass.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Session authentication filter.
 * <ul>
 * <li>Reads {@code UAEPASS_SESSION} cookie</li>
 * <li>Looks up session in DB</li>
 * <li>Checks expiry</li>
 * <li>Sets SecurityContext with authenticated principal</li>
 * <li>Returns 401 JSON for Angular (no redirect)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionAuthFilter extends OncePerRequestFilter {

    public static final String SESSION_COOKIE_NAME = "UAEPASS_SESSION";

    /**
     * Routes where the filter is completely skipped (no cookie check at all).
     * These are public endpoints that never need session context.
     */
    private static final List<String> SKIP_PATHS = List.of(
            "/auth/login", "/auth/callback", "/auth/logout",
            "/auth/link", "/auth/link/callback",
            "/signature/callback",
            "/hashsign/callback",
            "/face/verify/callback");

    private final UserSessionRepository sessionRepository;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/public/") || path.startsWith("/actuator/")) {
            return true;
        }
        return SKIP_PATHS.stream().anyMatch(path::equals);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String sessionToken = extractSessionCookie(request);

        if (sessionToken == null) {
            sendUnauthorized(response, "No session cookie");
            return;
        }

        Optional<UserSession> sessionOpt = sessionRepository.findBySessionToken(sessionToken);

        if (sessionOpt.isEmpty()) {
            sendUnauthorized(response, "Invalid session");
            return;
        }

        UserSession session = sessionOpt.get();

        // Check expiry
        if (session.getTokenExpires() != null && session.getTokenExpires().isBefore(OffsetDateTime.now())) {
            log.debug("Session expired for user {}", session.getUserId());
            sendUnauthorized(response, "Session expired");
            return;
        }

        // Update last_active
        sessionRepository.updateLastActive(session.getId());

        // Load user
        Optional<User> userOpt = userRepository.findById(session.getUserId());
        if (userOpt.isEmpty()) {
            sendUnauthorized(response, "User not found");
            return;
        }

        User user = userOpt.get();

        // Set Spring Security context
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getId().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Set request attribute for downstream controllers
        request.setAttribute("currentUser", user);

        filterChain.doFilter(request, response);
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

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
    }
}
