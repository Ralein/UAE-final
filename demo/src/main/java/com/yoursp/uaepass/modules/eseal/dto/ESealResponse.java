package com.yoursp.uaepass.modules.eseal.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ESealResponse {
    private final UUID jobId;
    private final String downloadUrl;
}
