package com.yoursp.uaepass.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler that produces clean, safe error responses.
 * Stack traces are NEVER exposed in response bodies.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String CORRELATION_ID_KEY = "correlationId";

    /**
     * Handles bean-validation failures (e.g. @Valid on @RequestBody).
     * Returns 400 with a list of field-level errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> {
                    Map<String, String> error = new HashMap<>();
                    error.put("field", fe.getField());
                    error.put("message", fe.getDefaultMessage());
                    return error;
                })
                .toList();

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("fieldErrors", fieldErrors);
        body.put("correlationId", MDC.get(CORRELATION_ID_KEY));

        log.warn("Validation failed: {} field error(s)", fieldErrors.size());

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Catch-all handler for unhandled exceptions.
     * Returns 500 with correlation ID — never exposes stack traces.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {

        String correlationId = MDC.get(CORRELATION_ID_KEY);
        log.error("Unhandled exception [correlationId={}]: {}", correlationId, ex.getMessage(), ex);

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", "An unexpected error occurred. Please reference correlationId for support.");
        body.put("correlationId", correlationId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
