package com.yoursp.uaepass.modules.signature;

import com.yoursp.uaepass.model.entity.SigningJob;
import com.yoursp.uaepass.repository.SigningJobRepository;
import com.yoursp.uaepass.service.AuditService;
import com.yoursp.uaepass.service.storage.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class SignatureCompletionServiceTest {

    @Mock
    private SpTokenService spTokenService;

    @Mock
    private SigningJobRepository jobRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private LtvService ltvService;

    @Mock
    private AuditService auditService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private SignatureCompletionService completionService;

    @Test
    @DisplayName("Canceled callback → status = CANCELED")
    void canceledCallback() {
        SigningJob job = SigningJob.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .signerProcessId("proc-1")
                .status("AWAITING_USER")
                .build();

        when(jobRepository.findBySignerProcessId("proc-1")).thenReturn(Optional.of(job));
        when(jobRepository.save(any(SigningJob.class))).thenAnswer(inv -> inv.getArgument(0));

        completionService.completeSign("proc-1", "canceled");

        assertEquals("CANCELED", job.getStatus());
        assertEquals("canceled", job.getCallbackStatus());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    @DisplayName("Failed callback → status = FAILED")
    void failedCallback() {
        SigningJob job = SigningJob.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .signerProcessId("proc-2")
                .status("AWAITING_USER")
                .build();

        when(jobRepository.findBySignerProcessId("proc-2")).thenReturn(Optional.of(job));
        when(jobRepository.save(any(SigningJob.class))).thenAnswer(inv -> inv.getArgument(0));

        completionService.completeSign("proc-2", "failed");

        assertEquals("FAILED", job.getStatus());
    }

    @Test
    @DisplayName("Failed_documents callback → status = FAILED_DOCUMENTS")
    void failedDocumentsCallback() {
        SigningJob job = SigningJob.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .signerProcessId("proc-3")
                .status("AWAITING_USER")
                .build();

        when(jobRepository.findBySignerProcessId("proc-3")).thenReturn(Optional.of(job));
        when(jobRepository.save(any(SigningJob.class))).thenAnswer(inv -> inv.getArgument(0));

        completionService.completeSign("proc-3", "failed_documents");

        assertEquals("FAILED_DOCUMENTS", job.getStatus());
    }

    @Test
    @DisplayName("Unknown signerProcessId → logs error and returns")
    void unknownProcessId() {
        when(jobRepository.findBySignerProcessId("unknown")).thenReturn(Optional.empty());

        // Should not throw — just log and return
        assertDoesNotThrow(() -> completionService.completeSign("unknown", "finished"));

        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("State transitions follow: AWAITING_USER → CALLBACK_RECEIVED → final status")
    void stateTransitionVerification() {
        SigningJob job = SigningJob.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .signerProcessId("proc-state")
                .status("AWAITING_USER")
                .build();

        when(jobRepository.findBySignerProcessId("proc-state")).thenReturn(Optional.of(job));
        when(jobRepository.save(any(SigningJob.class))).thenAnswer(inv -> inv.getArgument(0));

        completionService.completeSign("proc-state", "canceled");

        // Verify save was called at least twice (CALLBACK_RECEIVED + CANCELED)
        verify(jobRepository, atLeast(2)).save(any(SigningJob.class));
        // Final state should be CANCELED
        assertEquals("CANCELED", job.getStatus());
        assertEquals("canceled", job.getCallbackStatus());
    }
}
