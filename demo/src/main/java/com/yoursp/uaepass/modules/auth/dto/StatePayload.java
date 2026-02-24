package com.yoursp.uaepass.modules.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload stored in Redis alongside the OAuth2 state parameter.
 */
public record StatePayload(
        String flowType,
        String redirectAfter,
        UUID userId,
        Instant createdAt) {
}
