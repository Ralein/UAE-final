package com.yoursp.uaepass.modules.face.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FaceVerifyInitiateRequest {

    @NotBlank(message = "purpose is required")
    private String purpose;

    @NotBlank(message = "transactionRef is required")
    private String transactionRef;

    @NotBlank(message = "usernameType is required")
    @Pattern(regexp = "^(EID|MOBILE|EMAIL)$", message = "usernameType must be EID, MOBILE, or EMAIL")
    private String usernameType;
}
