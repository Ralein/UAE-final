package com.yoursp.uaepass.modules.hashsigning.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Result from calling the Hash Signing Docker SDK {@code POST /start}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HashStartResult {
    /** Unique transaction ID for this signing attempt. */
    private String txId;
    /** Sign identity ID returned by the SDK. */
    private String signIdentityId;
    /** SHA-256 hex digest of the prepared PDF (with ByteRange reservation). */
    private String digest;
}
