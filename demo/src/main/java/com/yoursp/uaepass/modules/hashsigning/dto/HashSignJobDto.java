package com.yoursp.uaepass.modules.hashsigning.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class HashSignJobDto {
    private final UUID jobId;
    private final String signingUrl;
}
