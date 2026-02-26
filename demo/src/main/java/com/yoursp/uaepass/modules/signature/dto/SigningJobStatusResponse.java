package com.yoursp.uaepass.modules.signature.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Response for GET /signature/status/{jobId}.
 * Angular polls this while status is AWAITING_USER or COMPLETING.
 */
@Getter
@Builder
public class SigningJobStatusResponse {

    private UUID jobId;
    private String status;
    private String downloadUrl;
    private String errorMessage;
    private boolean ltvApplied;
}
