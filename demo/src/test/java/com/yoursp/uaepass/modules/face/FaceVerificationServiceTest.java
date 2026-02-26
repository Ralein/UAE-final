package com.yoursp.uaepass.modules.face;

import com.yoursp.uaepass.model.entity.FaceVerification;
import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.auth.StateService;
import com.yoursp.uaepass.modules.face.dto.FaceVerifyInitiateRequest;
import com.yoursp.uaepass.modules.face.dto.FaceVerifyInitiateResponse;
import com.yoursp.uaepass.repository.FaceVerificationRepository;
import com.yoursp.uaepass.repository.UserRepository;
import com.yoursp.uaepass.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class FaceVerificationServiceTest {

        @Mock
        private FaceVerificationRepository faceRepo;
        @Mock
        private UserRepository userRepository;
        @Mock
        private StateService stateService;
        @Mock
        private AuditService auditService;
        @Mock
        private SecurityIncidentService securityIncidentService;

        @InjectMocks
        private FaceVerificationService service;

        @BeforeEach
        void setUp() {
                ReflectionTestUtils.setField(service, "uaepassBaseUrl", "https://stg-id.uaepass.ae");
                ReflectionTestUtils.setField(service, "clientId", "test-client");
                ReflectionTestUtils.setField(service, "appBaseUrl", "http://localhost:8080");
                ReflectionTestUtils.setField(service, "faceAcrValues", "urn:digitalid:authentication:flow:mobileid");
                ReflectionTestUtils.setField(service, "verificationWindowMinutes", 15);
        }

        // ================================================================
        // Initiate tests
        // ================================================================

        @Test
        @DisplayName("Initiate with MOBILE → success, returns auth URL with username param")
        void initiateWithMobileSuccess() {
                User user = User.builder()
                                .id(UUID.randomUUID())
                                .uaepassUuid("uuid-123")
                                .mobile("+971501234567")
                                .userType("SOP2")
                                .build();

                FaceVerifyInitiateRequest req = new FaceVerifyInitiateRequest();
                req.setPurpose("APPROVE_TRANSACTION");
                req.setTransactionRef("TX-001");
                req.setUsernameType("MOBILE");

                UUID verificationId = UUID.randomUUID();
                when(faceRepo.save(any(FaceVerification.class))).thenAnswer(inv -> {
                        FaceVerification fv = inv.getArgument(0);
                        fv.setId(verificationId);
                        return fv;
                });
                when(stateService.generateState(eq("FACE_VERIFY"), anyString(), eq(user.getId())))
                                .thenReturn("state-abc");

                FaceVerifyInitiateResponse response = service.initiate(user, req);

                assertNotNull(response);
                assertEquals(verificationId, response.getVerificationId());
                // Mobile number may be encoded as %2B971... or +971...
                assertTrue(response.getAuthorizationUrl().contains("username=")
                                && response.getAuthorizationUrl().contains("971501234567"));
                assertTrue(response.getAuthorizationUrl().contains("acr_values="));
                assertEquals(300, response.getExpiresInSeconds());
        }

        @Test
        @DisplayName("SOP1 user with EID → rejected with IllegalArgumentException")
        void sop1EidRejected() {
                User user = User.builder()
                                .id(UUID.randomUUID())
                                .userType("SOP1")
                                .idn("784-1990-1234567-1")
                                .build();

                FaceVerifyInitiateRequest req = new FaceVerifyInitiateRequest();
                req.setPurpose("TEST");
                req.setTransactionRef("TX-002");
                req.setUsernameType("EID");

                assertThrows(IllegalArgumentException.class, () -> service.initiate(user, req));
        }

        @Test
        @DisplayName("Missing mobile → rejected with IllegalArgumentException")
        void missingMobileRejected() {
                User user = User.builder()
                                .id(UUID.randomUUID())
                                .userType("SOP2")
                                .mobile(null)
                                .build();

                FaceVerifyInitiateRequest req = new FaceVerifyInitiateRequest();
                req.setPurpose("TEST");
                req.setTransactionRef("TX-003");
                req.setUsernameType("MOBILE");

                assertThrows(IllegalArgumentException.class, () -> service.initiate(user, req));
        }

        // ================================================================
        // Complete tests
        // ================================================================

        @Test
        @DisplayName("UUID match → VERIFIED status, audit logged")
        void uuidMatchSuccess() {
                UUID verificationId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();

                FaceVerification fv = FaceVerification.builder()
                                .id(verificationId)
                                .userId(userId)
                                .purpose("APPROVE")
                                .transactionRef("TX-100")
                                .status("PENDING")
                                .build();

                User user = User.builder()
                                .id(userId)
                                .uaepassUuid("matching-uuid")
                                .build();

                when(faceRepo.findById(verificationId)).thenReturn(Optional.of(fv));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(faceRepo.save(any(FaceVerification.class))).thenAnswer(inv -> inv.getArgument(0));

                boolean result = service.complete(verificationId, userId, "matching-uuid", "1.2.3.4");

                assertTrue(result);
                assertEquals("VERIFIED", fv.getStatus());
                assertTrue(fv.getUuidMatch());
                assertNotNull(fv.getVerifiedAt());
                assertEquals("matching-uuid", fv.getVerifiedUuid());

                verify(auditService).log(eq(userId), eq("FACE_VERIFY_SUCCESS"),
                                eq("FaceVerification"), anyString(), isNull(), anyMap());
                verify(securityIncidentService, never()).logSecurityIncident(anyString(), any(), anyMap());
        }

        @Test
        @DisplayName("UUID mismatch → SECURITY INCIDENT logged, returns false")
        void uuidMismatchSecurityIncident() {
                UUID verificationId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();

                FaceVerification fv = FaceVerification.builder()
                                .id(verificationId)
                                .userId(userId)
                                .purpose("HIGH_VALUE_SIGNING")
                                .transactionRef("TX-999")
                                .status("PENDING")
                                .build();

                User user = User.builder()
                                .id(userId)
                                .uaepassUuid("expected-uuid")
                                .build();

                when(faceRepo.findById(verificationId)).thenReturn(Optional.of(fv));
                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(faceRepo.save(any(FaceVerification.class))).thenAnswer(inv -> inv.getArgument(0));

                boolean result = service.complete(verificationId, userId, "DIFFERENT-uuid", "5.6.7.8");

                assertFalse(result);
                assertEquals("FAILED", fv.getStatus());
                assertFalse(fv.getUuidMatch());

                verify(securityIncidentService).logSecurityIncident(
                                eq("FACE_UUID_MISMATCH"), eq(userId), anyMap());
                verify(auditService, never()).log(eq(userId), eq("FACE_VERIFY_SUCCESS"),
                                anyString(), anyString(), any(), anyMap());
        }

        // ================================================================
        // hasRecentVerification tests
        // ================================================================

        @Test
        @DisplayName("Recent verification exists → returns true")
        void hasRecentVerificationTrue() {
                UUID userId = UUID.randomUUID();
                FaceVerification fv = FaceVerification.builder()
                                .id(UUID.randomUUID())
                                .verifiedAt(OffsetDateTime.now().minusMinutes(5))
                                .build();

                when(faceRepo.findRecentVerified(eq(userId), any(OffsetDateTime.class)))
                                .thenReturn(Optional.of(fv));

                assertTrue(service.hasRecentVerification(userId));
        }

        @Test
        @DisplayName("No recent verification (expired) → returns false")
        void hasRecentVerificationFalseExpired() {
                UUID userId = UUID.randomUUID();

                when(faceRepo.findRecentVerified(eq(userId), any(OffsetDateTime.class)))
                                .thenReturn(Optional.empty());

                assertFalse(service.hasRecentVerification(userId));
        }
}
