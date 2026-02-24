package com.yoursp.uaepass.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from UAE PASS /idshub/introspect endpoint.
 */
@Data
@NoArgsConstructor
public class TokenIntrospectResponse {

    private boolean active;

    @JsonProperty("client_id")
    private String clientId;

    private String sub;
    private long exp;

    @JsonProperty("userType")
    private String userType;
}
