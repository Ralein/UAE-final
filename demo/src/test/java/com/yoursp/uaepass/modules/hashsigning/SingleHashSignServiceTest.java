package com.yoursp.uaepass.modules.hashsigning;

import com.yoursp.uaepass.model.entity.SigningJob;
import com.yoursp.uaepass.modules.auth.StateService;
import com.yoursp.uaepass.modules.hashsigning.dto.HashSignInitiateRequest;
import com.yoursp.uaepass.modules.hashsigning.dto.HashSignJobDto;
import com.yoursp.uaepass.modules.hashsigning.dto.HashStartResult;
import com.yoursp.uaepass.modules.signature.LtvService;
import com.yoursp.uaepass.repository.SigningJobRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class SingleHashSignServiceTest {

        @Mock
        private HashSignSdkClient sdkClient;
        @Mock
        private StateService stateService;
        @Mock
        private SigningJobRepository jobRepository;
        @Mock
        private StorageService storageService;
        @Mock
        private LtvService ltvService;
        @Mock
        private AuditService auditService;

        @InjectMocks
        private SingleHashSignService service;

        @BeforeEach
        void setUp() {
                ReflectionTestUtils.setField(service, "uaepassBaseUrl", "https://stg-id.uaepass.ae");
                ReflectionTestUtils.setField(service, "clientId", "test-client");
                ReflectionTestUtils.setField(service, "appBaseUrl", "http://localhost:8080");
                ReflectionTestUtils.setField(service, "hashSignScope",
                                "urn:uae:digitalid:backend_api:hash_signing urn:safelayer:eidas:sign:identity:use:server");
        }

        @Test
        @DisplayName("Initiate creates job, calls SDK, returns signingUrl with correct params")
        void initiateSuccess() {
                UUID userId = UUID.randomUUID();
                UUID jobId = UUID.randomUUID();

                HashSignInitiateRequest request = new HashSignInitiateRequest();
                request.setFileName("test.pdf");
                request.setFileBase64("dGVzdA=="); // "test" in base64
                request.setPageNumber(1);
                request.setX(100);
                request.setY(200);
                request.setWidth(150);
                request.setHeight(50);

                HashStartResult startResult = new HashStartResult("tx-123", "sid-abc", "deadbeef");
                when(sdkClient.startProcess(any(byte[].class), eq("1:[100,200,150,50]")))
                                .thenReturn(startResult);

                when(jobRepository.save(any(SigningJob.class))).thenAnswer(inv -> {
                        SigningJob j = inv.getArgument(0);
                        if (j.getId() == null)
                                j.setId(jobId);
                        return j;
                });

                when(stateService.generateState(eq("HASH_SIGN"), isNull(), eq(userId)))
                                .thenReturn("test-state-123");

                HashSignJobDto result = service.initiate(userId, "test".getBytes(), request);

                assertNotNull(result);
                assertEquals(jobId, result.getJobId());
                assertTrue(result.getSigningUrl().contains("hsign-as"));
                assertTrue(result.getSigningUrl().contains("digests_summary=deadbeef"));
                assertTrue(result.getSigningUrl().contains("sign_identity_id=sid-abc"));
                assertTrue(result.getSigningUrl().contains("state=test-state-123"));

                verify(storageService).upload(any(byte[].class),
                                contains("hashsign/unsigned/"), eq("application/pdf"));
                verify(auditService).log(eq(userId), eq("HASH_SIGN_INITIATED"),
                                eq("SigningJob"), anyString(), isNull(), anyMap());
        }

        @Test
        @DisplayName("Complete flow: SDK sign → LTV → store → SIGNED status")
        void completeSuccess() {
                UUID jobId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();

                SigningJob job = SigningJob.builder()
                                .id(jobId)
                                .userId(userId)
                                .signingType("HASH")
                                .status("AWAITING_USER")
                                .signIdentityId("sid-abc")
                                .documents("{\"txId\":\"tx-123\",\"digest\":\"deadbeef\",\"signProp\":\"1:[0,0,100,50]\"}")
                                .build();

                when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
                when(jobRepository.save(any(SigningJob.class))).thenAnswer(inv -> inv.getArgument(0));

                byte[] signedPdf = "%PDF-1.4 signed".getBytes();
                byte[] ltvPdf = "%PDF-1.4 signed+ltv".getBytes();

                when(sdkClient.signDocument("tx-123", "sid-abc", "access-token"))
                                .thenReturn(signedPdf);
                when(ltvService.applyLtv(eq(signedPdf), eq(jobId)))
                                .thenReturn(ltvPdf);

                service.complete(jobId, "access-token");

                assertEquals("SIGNED", job.getStatus());
                assertTrue(job.getLtvApplied());
                assertNotNull(job.getCompletedAt());

                verify(storageService).upload(eq(signedPdf),
                                contains("hashsign/signed/"), eq("application/pdf"));
                verify(storageService).upload(eq(ltvPdf),
                                contains("hashsign/signed-ltv/"), eq("application/pdf"));
        }

        @Test
        @DisplayName("Complete with SDK failure → FAILED status")
        void completeSdkFailure() {
                UUID jobId = UUID.randomUUID();

                SigningJob job = SigningJob.builder()
                                .id(jobId)
                                .userId(UUID.randomUUID())
                                .signingType("HASH")
                                .status("AWAITING_USER")
                                .signIdentityId("sid-abc")
                                .documents("{\"txId\":\"tx-fail\"}")
                                .build();

                when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
                when(jobRepository.save(any(SigningJob.class))).thenAnswer(inv -> inv.getArgument(0));
                when(sdkClient.signDocument(anyString(), anyString(), anyString()))
                                .thenThrow(new HashSignSdkUnavailableException("SDK down"));

                service.complete(jobId, "token");

                assertEquals("FAILED", job.getStatus());
                assertNotNull(job.getErrorMessage());
        }
}
