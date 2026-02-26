package com.yoursp.uaepass.modules.eseal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ESealSoapClientTest {

    private ESealSoapClient soapClient;

    @BeforeEach
    void setUp() {
        soapClient = new ESealSoapClient();
        ReflectionTestUtils.setField(soapClient, "clientId", "test-client");
        ReflectionTestUtils.setField(soapClient, "clientSecret", "test-secret");
    }

    @Test
    @DisplayName("SOAP call to invalid endpoint → throws RuntimeException")
    void invalidEndpointThrows() {
        assertThrows(RuntimeException.class,
                () -> soapClient.executeSoapRequest(
                        "http://invalid-host-that-does-not-exist.local/soap",
                        "<SignRequest/>",
                        "req-001"));
    }

    @Test
    @DisplayName("ESealUnavailableException has correct message")
    void unavailableExceptionMessage() {
        ESealUnavailableException ex = new ESealUnavailableException("Service down");
        assertEquals("Service down", ex.getMessage());
    }

    @Test
    @DisplayName("ErrorCodeMapper maps known codes correctly")
    void errorCodeMapperKnownCodes() {
        assertEquals("Invalid eSeal signature",
                ESealErrorCodeMapper.toMessage(
                        "urn:oasis:names:tc:dss:1.0:resultminor:invalid:IncorrectSignature"));

        assertEquals("eSeal certificate key lookup failed — verify ESEAL_CERT_SUBJECT_NAME",
                ESealErrorCodeMapper.toMessage(
                        "urn:oasis:names:tc:dss:1.0:resultminor:KeyLookupFailed"));
    }

    @Test
    @DisplayName("ErrorCodeMapper returns raw URI for unknown codes")
    void errorCodeMapperUnknownCode() {
        String unknown = "urn:some:unknown:code";
        assertEquals(unknown, ESealErrorCodeMapper.toMessage(unknown));
    }

    @Test
    @DisplayName("ErrorCodeMapper handles null/blank input")
    void errorCodeMapperNullBlank() {
        assertEquals("No additional error details", ESealErrorCodeMapper.toMessage(null));
        assertEquals("No additional error details", ESealErrorCodeMapper.toMessage(""));
    }
}
