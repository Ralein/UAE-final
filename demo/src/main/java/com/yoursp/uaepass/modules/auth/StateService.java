package com.yoursp.uaepass.modules.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursp.uaepass.modules.auth.dto.StatePayload;
import com.yoursp.uaepass.modules.auth.exception.InvalidStateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages OAuth2 state parameters in Redis.
 * <ul>
 * <li>State is stored with a 300s TTL (5 minutes)</li>
 * <li>Consumption is atomic via Lua script (GET + DEL in one round-trip)</li>
 * <li>Each state can only be consumed once — replay attacks are prevented</li>
 * </ul>
 */
@SuppressWarnings("null")
@Slf4j
@Service
@RequiredArgsConstructor
public class StateService {

    private static final String KEY_PREFIX = "oauth_state:";
    private static final long STATE_TTL_SECONDS = 300; // 5 minutes

    /**
     * Lua script: atomically GET then DEL. Returns the value or nil.
     * This ensures the state can only ever be consumed once, even under
     * concurrent requests.
     */
    private static final String CONSUME_LUA_SCRIPT = "local val = redis.call('GET', KEYS[1]) " +
            "if val then redis.call('DEL', KEYS[1]) end " +
            "return val";

    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(CONSUME_LUA_SCRIPT,
            String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Generate a new OAuth2 state parameter and store its payload in Redis.
     *
     * @param flowType      e.g. "AUTH", "LINK", "SIGN"
     * @param redirectAfter URL to redirect to after successful auth
     * @param userId        optional user ID (null for initial auth)
     * @return the state string (a UUID)
     */
    public String generateState(String flowType, String redirectAfter, UUID userId) {
        String state = UUID.randomUUID().toString();

        StatePayload payload = new StatePayload(flowType, redirectAfter, userId, Instant.now());

        try {
            String json = objectMapper.writeValueAsString(payload);
            String key = KEY_PREFIX + state;
            redisTemplate.opsForValue().set(key, json, STATE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Generated OAuth state: {} (flow={})", state, flowType);
            return state;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize state payload", e);
        }
    }

    /**
     * Atomically consume a state parameter from Redis.
     * The state is deleted immediately — it cannot be reused.
     *
     * @param state the state string from the callback
     * @return the associated StatePayload
     * @throws InvalidStateException if the state is invalid, expired, or already
     *                               consumed
     */
    public StatePayload consumeState(String state) {
        if (state == null || state.isBlank()) {
            throw new InvalidStateException("State parameter is missing");
        }

        String key = KEY_PREFIX + state;

        // Atomic GET + DEL via Lua script (result may be null if key doesn't exist)
        String json = castToString(redisTemplate.execute(CONSUME_SCRIPT, Collections.singletonList(key)));

        if (json == null) {
            log.warn("OAuth state not found or expired: {}", state);
            throw new InvalidStateException("Invalid or expired state parameter");
        }

        try {
            StatePayload payload = objectMapper.readValue(json, StatePayload.class);
            log.debug("Consumed OAuth state: {} (flow={})", state, payload.flowType());
            return payload;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize state payload", e);
        }
    }

    private String castToString(Object obj) {
        return obj != null ? obj.toString() : null;
    }
}
