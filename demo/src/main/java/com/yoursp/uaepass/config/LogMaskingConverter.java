package com.yoursp.uaepass.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.CompositeConverter;

import java.util.regex.Pattern;

/**
 * Logback converter that masks sensitive data in log messages.
 * <ul>
 * <li>access_token / Bearer tokens: first 8 chars + "..."</li>
 * <li>client_secret: "[REDACTED]"</li>
 * <li>Emirates ID (idn): "EID:[REDACTED]"</li>
 * <li>Mobile numbers: last 4 digits only (***1234)</li>
 * </ul>
 * <p>
 * Register in logback-spring.xml:
 * {@code <conversionRule conversionWord="mask" converterClass=
 * "com.yoursp.uaepass.config.LogMaskingConverter" />}
 * </p>
 */
public class LogMaskingConverter extends CompositeConverter<ILoggingEvent> {

    // Matches Bearer tokens: "Bearer <token>"
    private static final Pattern BEARER_PATTERN = Pattern
            .compile("(Bearer\\s+)([A-Za-z0-9_\\-./+=]{8})[A-Za-z0-9_\\-./+=]+");

    // Matches access_token=<value> or "access_token":"<value>"
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern
            .compile("(access_token[\"=:]+\\s*[\"']?)([A-Za-z0-9_\\-./+=]{8})[A-Za-z0-9_\\-./+=]+");

    // Matches client_secret=<value> or "client_secret":"<value>"
    private static final Pattern CLIENT_SECRET_PATTERN = Pattern.compile("(client_secret[\"=:]+\\s*[\"']?)[^\"&\\s,]+");

    // Matches UAE Emirates ID patterns (784-YYYY-NNNNNNN-N)
    private static final Pattern EID_PATTERN = Pattern.compile("784-\\d{4}-\\d{7}-\\d");

    // Matches mobile numbers starting with + followed by digits
    private static final Pattern MOBILE_PATTERN = Pattern.compile("(\\+\\d{1,4})(\\d+)(\\d{4})");

    @Override
    protected String transform(ILoggingEvent event, String formattedMessage) {
        if (formattedMessage == null || formattedMessage.isEmpty()) {
            return formattedMessage;
        }

        String masked = formattedMessage;
        masked = BEARER_PATTERN.matcher(masked).replaceAll("$1$2...");
        masked = ACCESS_TOKEN_PATTERN.matcher(masked).replaceAll("$1$2...");
        masked = CLIENT_SECRET_PATTERN.matcher(masked).replaceAll("$1[REDACTED]");
        masked = EID_PATTERN.matcher(masked).replaceAll("EID:[REDACTED]");
        masked = MOBILE_PATTERN.matcher(masked).replaceAll("***$3");

        return masked;
    }
}
