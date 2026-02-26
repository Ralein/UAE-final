package com.yoursp.uaepass.modules.signature.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Individual document entry for multi-doc signing requests.
 */
@Getter
@Setter
public class MultiDocRequest {

    @NotBlank
    private String fileName;

    @NotBlank
    private String fileBase64;
}
