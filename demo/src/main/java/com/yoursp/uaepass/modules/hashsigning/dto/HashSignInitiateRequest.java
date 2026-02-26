package com.yoursp.uaepass.modules.hashsigning.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class HashSignInitiateRequest {

    @NotBlank(message = "fileName is required")
    private String fileName;

    @NotBlank(message = "fileBase64 is required")
    private String fileBase64;

    @Min(value = 1, message = "pageNumber must be >= 1")
    private int pageNumber;

    @Min(value = 0)
    private int x;

    @Min(value = 0)
    private int y;

    @Min(value = 1, message = "width must be >= 1")
    private int width;

    @Min(value = 1, message = "height must be >= 1")
    private int height;
}
