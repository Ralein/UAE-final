package com.yoursp.uaepass.modules.eseal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ESealResult {
    private final UUID jobId;
    private final byte[] sealedBytes;
    private final String requestId;
}
