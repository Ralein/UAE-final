package com.yoursp.uaepass.modules.signature.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Response for POST /signature/initiate.
 */
@Getter
@AllArgsConstructor
public class SignInitiateResponse {

    private UUID jobId;
    private String signingUrl;
}
