package com.yoursp.uaepass.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Sliding window rate limiter using Redis ZADD + ZREMRANGEBYSCORE.
 * <p>
 * Budget: ~500 extra Redis commands/day — within Upstash free tier.
 * </p>
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rate-limit.enabled:true}")
    private boolean enabled;

    public RateLimitFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        RateLimitConfig config = resolveConfig(path);

        if (config == null) {
            chain.doFilter(request, response);
            return;
        }

        String identifier = resolveIdentifier(request, config);
        String key = "ratelimit:" + config.endpointKey + ":" + identifier;

        if (isRateLimited(key, config.maxRequests, config.windowSeconds)) {
            log.warn("Rate limited: key={}, path={}", key, path);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(config.windowSeconds));
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "error", "RATE_LIMITED",
                    "message", "Too many requests. Try again later.",
                    "retryAfterSeconds", config.windowSeconds)));
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isRateLimited(String key, int maxRequests, int windowSeconds) {
        try {
            double now = Instant.now().toEpochMilli();
            double windowStart = now - (windowSeconds * 1000.0);

            // Remove expired entries
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

            // Count current entries
            Long count = redisTemplate.opsForZSet().zCard(key);

            if (count != null && count >= maxRequests) {
                return true;
            }

            // Add current request
            redisTemplate.opsForZSet().add(key, String.valueOf(now), now);

            // Set key expiry (auto-cleanup)
            redisTemplate.expire(key, java.time.Duration.ofSeconds(windowSeconds + 10));

            return false;
        } catch (Exception e) {
            // On Redis failure, allow the request (fail-open)
            log.warn("Rate limit check failed (allowing request): {}", e.getMessage());
            return false;
        }
    }

    private RateLimitConfig resolveConfig(String path) {
        if (path.equals("/auth/login")) {
            return new RateLimitConfig("auth_login", 10, 60, true);
        } else if (path.equals("/auth/callback")) {
            return new RateLimitConfig("auth_callback", 5, 60, true);
        } else if (path.equals("/auth/register")) {
            return new RateLimitConfig("auth_register", 3, 300, true);
        } else if (path.equals("/signature/initiate")) {
            return new RateLimitConfig("sig_initiate", 20, 3600, false);
        } else if (path.startsWith("/eseal/")) {
            return new RateLimitConfig("eseal", 50, 3600, false);
        } else if (path.equals("/face/verify/initiate")) {
            return new RateLimitConfig("face_verify", 10, 300, true);
        }
        return null;
    }

    private String resolveIdentifier(HttpServletRequest request, RateLimitConfig config) {
        if (config.useIp) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        // Use userId from session
        Object user = request.getAttribute("currentUser");
        if (user != null) {
            try {
                return user.getClass().getMethod("getId").invoke(user).toString();
            } catch (Exception e) {
                return request.getRemoteAddr();
            }
        }
        return request.getRemoteAddr();
    }

    private record RateLimitConfig(String endpointKey, int maxRequests, int windowSeconds, boolean useIp) {
    }
}
