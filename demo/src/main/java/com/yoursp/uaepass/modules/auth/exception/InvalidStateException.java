package com.yoursp.uaepass.modules.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an OAuth2 state parameter is invalid, expired, or already
 * consumed.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}
