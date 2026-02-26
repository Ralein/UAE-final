package com.yoursp.uaepass.modules.face.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class FaceVerifyInitiateResponse {
    private final UUID verificationId;
    private final String authorizationUrl;
    private final int expiresInSeconds;
}
