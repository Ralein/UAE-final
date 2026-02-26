package com.yoursp.uaepass.modules.eseal;

import java.util.Map;

/**
 * Maps UAE PASS eSeal ResultMinor URIs to human-readable error messages.
 */
public final class ESealErrorCodeMapper {

    private ESealErrorCodeMapper() {
    }

    private static final Map<String, String> ERROR_MAP = Map.ofEntries(
            Map.entry("urn:oasis:names:tc:dss:1.0:resultminor:invalid:IncorrectSignature",
                    "Invalid eSeal signature"),
            Map.entry("urn:oasis:names:tc:dss:1.0:resultminor:InvalidSignatureTimestamp",
                    "Invalid signature timestamp"),
            Map.entry("urn:oasis:names:tc:dss:1.0:resultminor:GeneralError",
                    "General eSeal processing error"),
            Map.entry("urn:oasis:names:tc:dss:1.0:resultminor:invalid:ReferdHash",
                    "Document hash reference is invalid"),
            Map.entry("urn:oasis:names:tc:dss:1.0:resultminor:NotSupported",
                    "Requested operation is not supported"),
            Map.entry("urn:oasis:names:tc:dss:1.0:resultminor:InsufficientInformation",
                    "Insufficient information provided for eSeal"),
            Map.entry("urn:oasis:names:tc:dss:1.0:resultminor:KeyLookupFailed",
                    "eSeal certificate key lookup failed — verify ESEAL_CERT_SUBJECT_NAME"),
            Map.entry("urn:oasis:names:tc:dss:1.0:resultminor:inappropriate:signature",
                    "Inappropriate signature format for the document type"),
            Map.entry("urn:safelayer:tws:dss:resultminor:certificate:revoked",
                    "eSeal certificate has been revoked"),
            Map.entry("urn:safelayer:tws:dss:resultminor:certificate:expired",
                    "eSeal certificate has expired"),
            Map.entry("urn:safelayer:tws:dss:resultminor:certificate:not_yet_valid",
                    "eSeal certificate is not yet valid"),
            Map.entry("urn:oasis:names:tc:dss:1.0:resultminor:valid:signature:InvalidSignatureTimestamp",
                    "Signature is valid but timestamp is invalid"),
            Map.entry("urn:oasis:names:tc:dss:1.0:resultminor:valid:signature:OnAllDocuments",
                    "Valid eSeal on all documents"));

    /**
     * Map a ResultMinor URI to a human-readable message.
     *
     * @param resultMinor the ResultMinor URI from the SOAP response
     * @return human-readable message, or the raw URI if not mapped
     */
    public static String toMessage(String resultMinor) {
        if (resultMinor == null || resultMinor.isBlank()) {
            return "No additional error details";
        }
        return ERROR_MAP.getOrDefault(resultMinor, resultMinor);
    }
}
