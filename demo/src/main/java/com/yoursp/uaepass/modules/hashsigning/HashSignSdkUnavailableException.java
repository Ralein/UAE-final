package com.yoursp.uaepass.modules.hashsigning;

/**
 * Thrown when the Hash Signing Docker SDK sidecar is unreachable.
 */
public class HashSignSdkUnavailableException extends RuntimeException {

    public HashSignSdkUnavailableException(String message) {
        super(message);
    }

    public HashSignSdkUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
