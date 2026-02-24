package com.yoursp.uaepass.modules.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursp.uaepass.modules.auth.dto.StatePayload;
import com.yoursp.uaepass.modules.auth.exception.InvalidStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private StateService stateService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        stateService = new StateService(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("generateState should store state in Redis with TTL")
    void generateStateShouldStoreInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String state = stateService.generateState("AUTH", "/dashboard", null);

        assertNotNull(state);
        // Verify it's a valid UUID
        assertDoesNotThrow(() -> UUID.fromString(state));

        verify(valueOperations).set(
                eq("oauth_state:" + state),
                anyString(),
                eq(300L),
                eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("generateState with userId should include userId in payload")
    void generateStateWithUserIdShouldIncludeUserId() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID userId = UUID.randomUUID();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

        stateService.generateState("LINK", "/settings", userId);

        verify(valueOperations).set(anyString(), jsonCaptor.capture(), anyLong(), any());

        StatePayload payload = objectMapper.readValue(jsonCaptor.getValue(), StatePayload.class);
        assertEquals("LINK", payload.flowType());
        assertEquals("/settings", payload.redirectAfter());
        assertEquals(userId, payload.userId());
        assertNotNull(payload.createdAt());
    }

    @Test
    @DisplayName("consumeState should atomically GET and DEL from Redis")
    void consumeStateShouldAtomicallyGetAndDel() throws Exception {
        UUID userId = UUID.randomUUID();
        String stateJson = objectMapper.writeValueAsString(
                new StatePayload("AUTH", "/dashboard", userId, java.time.Instant.now()));

        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                .thenReturn(stateJson);

        StatePayload result = stateService.consumeState("some-state-uuid");

        assertNotNull(result);
        assertEquals("AUTH", result.flowType());
        assertEquals("/dashboard", result.redirectAfter());
        assertEquals(userId, result.userId());

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("oauth_state:some-state-uuid")));
    }

    @Test
    @DisplayName("consumeState should throw InvalidStateException when state not found")
    void consumeStateShouldThrowWhenNotFound() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                .thenReturn(null);

        assertThrows(InvalidStateException.class, () -> stateService.consumeState("nonexistent-state"));
    }

    @Test
    @DisplayName("consumeState should throw InvalidStateException for null state")
    void consumeStateShouldThrowForNullState() {
        assertThrows(InvalidStateException.class, () -> stateService.consumeState(null));
    }

    @Test
    @DisplayName("consumeState should throw InvalidStateException for blank state")
    void consumeStateShouldThrowForBlankState() {
        assertThrows(InvalidStateException.class, () -> stateService.consumeState("   "));
    }

    @Test
    @DisplayName("consumeState called twice should only succeed once (state is deleted)")
    void consumeStateShouldOnlySucceedOnce() throws Exception {
        String stateJson = objectMapper.writeValueAsString(
                new StatePayload("AUTH", "/dashboard", null, java.time.Instant.now()));

        // First call returns the value, second call returns null (already deleted)
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList()))
                .thenReturn(stateJson)
                .thenReturn(null);

        // First consume succeeds
        StatePayload result = stateService.consumeState("state-uuid");
        assertNotNull(result);

        // Second consume fails
        assertThrows(InvalidStateException.class, () -> stateService.consumeState("state-uuid"));
    }
}
