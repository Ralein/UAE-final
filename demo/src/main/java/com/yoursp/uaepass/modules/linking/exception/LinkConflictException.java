package com.yoursp.uaepass.modules.linking.exception;

import lombok.Getter;

/**
 * Thrown when linking violates the one-to-one mapping constraint.
 */
@Getter
public class LinkConflictException extends RuntimeException {

    private final String errorCode;

    public LinkConflictException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
