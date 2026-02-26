package com.yoursp.uaepass.modules.signature;

import com.yoursp.uaepass.config.UaePassProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manages the SP-level access token (client_credentials grant).
 * <ul>
 * <li>Used for signing API calls (NOT user-level token)</li>
 * <li>Cached in Redis with key {@code sp_sign_token}</li>
 * <li>TTL = expires_in - 60 seconds (buffer before actual expiry)</li>
 * <li>Circuit breaker: opens after 3 failures, half-open after 30s</li>
 * </ul>
 */
@Slf4j
@Service
public class SpTokenService {

    private static final String REDIS_KEY = "sp_sign_token";
    private static final long TTL_BUFFER_SECONDS = 60;

    private final WebClient webClient;
    private final StringRedisTemplate redisTemplate;
    private final UaePassProperties uaePassProperties;

    @Value("${signature.sp-token-url}")
    private String spTokenUrl;

    @Value("${signature.sign-scope}")
    private String signScope;

    public SpTokenService(WebClient.Builder webClientBuilder,
            StringRedisTemplate redisTemplate,
            UaePassProperties uaePassProperties) {
        this.webClient = webClientBuilder.build();
        this.redisTemplate = redisTemplate;
        this.uaePassProperties = uaePassProperties;
    }

    /**
     * Get a valid SP access token. Returns cached token if available.
     */
    @CircuitBreaker(name = "spToken", fallbackMethod = "getSpTokenFallback")
    public String getSpAccessToken() {
        // Check Redis cache first
        String cached = redisTemplate.opsForValue().get(REDIS_KEY);
        if (cached != null) {
            log.debug("Using cached SP token (first 8 chars: {}...)",
                    cached.substring(0, Math.min(8, cached.length())));
            return cached;
        }

        // Fetch new token via client_credentials grant
        log.info("Fetching new SP access token from {}", spTokenUrl);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient.post()
                .uri(spTokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(h -> h.setBasicAuth(
                        uaePassProperties.getClientId(),
                        uaePassProperties.getClientSecret()))
                .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                        .with("scope", signScope))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || response.get("access_token") == null) {
            throw new RuntimeException("Failed to obtain SP access token — null response");
        }

        String token = (String) response.get("access_token");
        int expiresIn = response.get("expires_in") instanceof Number
                ? ((Number) response.get("expires_in")).intValue()
                : 3600;

        // Cache with buffer
        long ttl = Math.max(expiresIn - TTL_BUFFER_SECONDS, 60);
        redisTemplate.opsForValue().set(REDIS_KEY, token, ttl, TimeUnit.SECONDS);

        log.info("SP token cached for {}s (first 8 chars: {}...)", ttl,
                token.substring(0, Math.min(8, token.length())));

        return token;
    }

    @SuppressWarnings("unused")
    private String getSpTokenFallback(Throwable t) {
        log.error("Circuit breaker open — SP token fetch failed: {}", t.getMessage());
        // Try returning cached even if technically expired
        String cached = redisTemplate.opsForValue().get(REDIS_KEY);
        if (cached != null) {
            log.warn("Returning possibly stale cached SP token");
            return cached;
        }
        throw new RuntimeException("SP token unavailable — circuit breaker open", t);
    }
}
