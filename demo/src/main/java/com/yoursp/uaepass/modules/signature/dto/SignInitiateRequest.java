package com.yoursp.uaepass.modules.signature.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for POST /signature/initiate.
 */
@Getter
@Setter
public class SignInitiateRequest {

    @NotBlank(message = "fileName is required")
    private String fileName;

    @NotBlank(message = "fileBase64 is required")
    private String fileBase64;

    private String signatureFieldName = "Signature1";

    @Min(value = 1, message = "pageNumber must be >= 1")
    private int pageNumber = 1;

    @Min(0)
    private int x = 100;

    @Min(0)
    private int y = 100;

    @Min(10)
    private int width = 200;

    @Min(10)
    private int height = 100;

    private boolean showSignatureImage = false;
}
