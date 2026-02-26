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
class PadesESealServiceTest {

    @Mock
    private ESealSoapClient soapClient;
    @Mock
    private EsealJobRepository jobRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private PadesESealService padesService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(padesService, "soapEndpoint",
                "https://eseal-stg.uaepass.ae/sign");
        ReflectionTestUtils.setField(padesService, "certSubjectName",
                "CN=Test eSeal, O=Test, L=Dubai, C=AE");
    }

    @Test
    @DisplayName("Successful PAdES seal → returns sealed PDF bytes")
    void successfulPadesSeal() {
        byte[] pdfBytes = "%PDF-1.4 test content".getBytes();
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        // Mock SOAP response with Base64-encoded sealed PDF
        byte[] sealedPdf = "%PDF-1.4 sealed content".getBytes();
        String b64Sealed = Base64.getEncoder().encodeToString(sealedPdf);
        String soapResponse = """
                <SignResponse>
                  <Result><ResultMajor>urn:oasis:names:tc:dss:1.0:resultmajor:Success</ResultMajor></Result>
                  <OptionalOutputs>
                    <DocumentWithSignature>
                      <XMLData><Base64Data MimeType="application/pdf">%s</Base64Data></XMLData>
                    </DocumentWithSignature>
                  </OptionalOutputs>
                </SignResponse>
                """.formatted(b64Sealed);

        when(soapClient.executeSoapRequest(anyString(), anyString(), anyString()))
                .thenReturn(soapResponse);
        when(jobRepository.save(any(EsealJob.class))).thenAnswer(inv -> {
            EsealJob j = inv.getArgument(0);
            if (j.getId() == null)
                j.setId(jobId);
            return j;
        });

        ESealResult result = padesService.sealPdf(pdfBytes, userId);

        assertNotNull(result);
        assertEquals(jobId, result.getJobId());
        assertArrayEquals(sealedPdf, result.getSealedBytes());
        assertNotNull(result.getRequestId());

        // Verify storage was called
        verify(storageService, times(2)).upload(any(byte[].class), anyString(), anyString());
        verify(auditService).log(eq(userId), eq("ESEAL_PDF"), eq("ESEAL_JOB"),
                anyString(), isNull(), anyMap());
    }

    @Test
    @DisplayName("Failed SOAP response → throws + records failed job")
    void failedSoapResponse() {
        byte[] pdfBytes = "%PDF-1.4 test".getBytes();
        UUID userId = UUID.randomUUID();

        String failResponse = """
                <SignResponse>
                  <Result>
                    <ResultMajor>urn:oasis:names:tc:dss:1.0:resultmajor:RequesterError</ResultMajor>
                    <ResultMinor>urn:oasis:names:tc:dss:1.0:resultminor:KeyLookupFailed</ResultMinor>
                  </Result>
                </SignResponse>
                """;

        when(soapClient.executeSoapRequest(anyString(), anyString(), anyString()))
                .thenReturn(failResponse);
        when(jobRepository.save(any(EsealJob.class))).thenAnswer(inv -> inv.getArgument(0));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> padesService.sealPdf(pdfBytes, userId));

        assertTrue(ex.getMessage().contains("PAdES eSeal failed"));
        verify(jobRepository).save(argThat(job -> "FAILED".equals(job.getStatus())));
    }

    @Test
    @DisplayName("Unconfigured endpoint → throws ESealUnavailableException")
    void unconfiguredEndpoint() {
        ReflectionTestUtils.setField(padesService, "soapEndpoint", "");

        assertThrows(ESealUnavailableException.class,
                () -> padesService.sealPdf("%PDF".getBytes(), UUID.randomUUID()));
    }
}
