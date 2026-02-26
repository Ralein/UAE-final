package com.yoursp.uaepass.modules.eseal;

/**
 * Thrown when the eSeal SOAP circuit breaker is open or the service is
 * unavailable.
 */
public class ESealUnavailableException extends RuntimeException {

    public ESealUnavailableException(String message) {
        super(message);
    }

    public ESealUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
