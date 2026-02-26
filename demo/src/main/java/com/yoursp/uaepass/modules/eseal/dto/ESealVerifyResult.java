package com.yoursp.uaepass.modules.eseal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ESealVerifyResult {
    private final boolean valid;
    private final String resultMajor;
    private final String resultMinor;
    private final String resultMessage;
    private final String signerName;
    private final String signingTime;
    private final String certificateInfo;
}
