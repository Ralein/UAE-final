package com.yoursp.uaepass.modules.eseal;

import com.yoursp.uaepass.model.entity.EsealJob;
import com.yoursp.uaepass.modules.eseal.dto.ESealResult;
import com.yoursp.uaepass.repository.EsealJobRepository;
import com.yoursp.uaepass.service.AuditService;
import com.yoursp.uaepass.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CadesESealServiceTest {

    @Mock
    private ESealSoapClient soapClient;
    @Mock
    private EsealJobRepository jobRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private CadesESealService cadesService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cadesService, "soapEndpoint",
                "https://eseal-stg.uaepass.ae/sign");
        ReflectionTestUtils.setField(cadesService, "certSubjectName",
                "CN=Test eSeal, O=Test, L=Dubai, C=AE");
    }

    @Test
    @DisplayName("Successful CAdES seal → returns PKCS#7 signature bytes")
    void successfulCadesSeal() {
        byte[] docBytes = "binary document content".getBytes();
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        byte[] signatureBytes = "pkcs7-signature-data".getBytes();
        String b64Sig = Base64.getEncoder().encodeToString(signatureBytes);
        String soapResponse = """
                <SignResponse>
                  <Result><ResultMajor>urn:oasis:names:tc:dss:1.0:resultmajor:Success</ResultMajor></Result>
                  <SignatureObject>
                    <Base64Signature>%s</Base64Signature>
                  </SignatureObject>
                </SignResponse>
                """.formatted(b64Sig);

        when(soapClient.executeSoapRequest(anyString(), anyString(), anyString()))
                .thenReturn(soapResponse);
        when(jobRepository.save(any(EsealJob.class))).thenAnswer(inv -> {
            EsealJob j = inv.getArgument(0);
            if (j.getId() == null)
                j.setId(jobId);
            return j;
        });

        ESealResult result = cadesService.sealDocument(docBytes, userId);

        assertNotNull(result);
        assertEquals(jobId, result.getJobId());
        assertArrayEquals(signatureBytes, result.getSealedBytes());

        // CAdES stores both .bin and .p7s
        verify(storageService).upload(eq(docBytes), contains(".bin"), eq("application/octet-stream"));
        verify(storageService).upload(eq(signatureBytes), contains(".p7s"), eq("application/pkcs7-signature"));
        verify(auditService).log(eq(userId), eq("ESEAL_DOCUMENT"), eq("ESEAL_JOB"),
                anyString(), isNull(), anyMap());
    }

    @Test
    @DisplayName("CAdES request contains correct profile and SignatureType")
    void cadesRequestContainsCorrectProfile() {
        byte[] docBytes = "test".getBytes();
        UUID userId = UUID.randomUUID();

        byte[] sigBytes = "sig".getBytes();
        String b64 = Base64.getEncoder().encodeToString(sigBytes);
        String response = """
                <SignResponse>
                  <Result><ResultMajor>urn:oasis:names:tc:dss:1.0:resultmajor:Success</ResultMajor></Result>
                  <SignatureObject><Base64Signature>%s</Base64Signature></SignatureObject>
                </SignResponse>
                """.formatted(b64);

        when(soapClient.executeSoapRequest(anyString(), argThat(body -> body.contains("cmspkcs7sig:1.0:sign") &&
                body.contains("urn:etsi:ts:101733") &&
                body.contains("EnvelopingSignature")),
                anyString()))
                .thenReturn(response);
        when(jobRepository.save(any(EsealJob.class))).thenAnswer(inv -> {
            EsealJob j = inv.getArgument(0);
            if (j.getId() == null)
                j.setId(UUID.randomUUID());
            return j;
        });

        ESealResult result = cadesService.sealDocument(docBytes, userId);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Unconfigured endpoint → throws ESealUnavailableException")
    void unconfiguredEndpoint() {
        ReflectionTestUtils.setField(cadesService, "soapEndpoint", "");

        assertThrows(ESealUnavailableException.class,
                () -> cadesService.sealDocument("test".getBytes(), UUID.randomUUID()));
    }
}
