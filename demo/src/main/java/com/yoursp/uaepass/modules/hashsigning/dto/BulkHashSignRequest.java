package com.yoursp.uaepass.modules.hashsigning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class BulkHashSignRequest {

    @NotEmpty(message = "At least one document is required")
    @Valid
    private List<BulkDoc> documents;
}
