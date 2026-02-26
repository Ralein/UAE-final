package com.yoursp.uaepass.modules.linking.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response for GET /auth/link/status.
 */
@Getter
@Builder
public class LinkStatusResponse {

    private boolean linked;
    private String linkedAt;
    private String userType;
}
