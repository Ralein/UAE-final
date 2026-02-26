package com.yoursp.uaepass.modules.face.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class FaceVerifyStatusResponse {
    private final UUID verificationId;
    private final String status;
    private final String purpose;
    private final OffsetDateTime verifiedAt;
}
