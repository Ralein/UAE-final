package com.yoursp.uaepass.modules.signature.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Result of signature verification via UAE PASS SOAP Verification API.
 */
@Getter
@Builder
public class VerificationResult {

    private boolean valid;
    private String signerName;
    private String signingTime;
    private String certificateInfo;
    private String resultMajor;
    private String resultMinor;
}
