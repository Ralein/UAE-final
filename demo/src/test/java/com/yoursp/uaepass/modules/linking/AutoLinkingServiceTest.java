package com.yoursp.uaepass.modules.linking;

import com.yoursp.uaepass.model.entity.User;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoLinkingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AutoLinkingService autoLinkingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(autoLinkingService, "idnEncryptionKey", "test-key-for-auto-link!!");
    }

    @Test
    @DisplayName("User already linked by uuid — should return existing user")
    void alreadyLinkedByUuid() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .uaepassUuid("uuid-123")
                .build();

        when(userRepository.findByUaepassUuid("uuid-123")).thenReturn(Optional.of(existing));

        User result = autoLinkingService.tryAutoLink("uuid-123", "idn-456", "SOP3");

        assertNotNull(result);
        assertEquals(existing.getId(), result.getId());
        // Should NOT log audit — not a new link
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SOP1 visitor — only uuid lookup, never idn fallback")
    void sop1VisitorUuidOnly() {
        when(userRepository.findByUaepassUuid("visitor-uuid")).thenReturn(Optional.empty());

        User result = autoLinkingService.tryAutoLink("visitor-uuid", null, "SOP1");

        assertNull(result);
        // Should never attempt idn lookup for SOP1
        verify(userRepository, never()).findByIdn(anyString());
    }

    @Test
    @DisplayName("SOP1 visitor with idn — should still NOT try idn fallback")
    void sop1WithIdnShouldNotAutoLink() {
        when(userRepository.findByUaepassUuid("visitor-uuid-2")).thenReturn(Optional.empty());

        User result = autoLinkingService.tryAutoLink("visitor-uuid-2", "some-idn", "SOP1");

        assertNull(result);
        verify(userRepository, never()).findByIdn(anyString());
    }

    @Test
    @DisplayName("SOP2 resident — auto-link by idn when uuid not found")
    void sop2AutoLinkByIdn() {
        User existingByIdn = User.builder()
                .id(UUID.randomUUID())
                .build();

        when(userRepository.findByUaepassUuid("new-uuid")).thenReturn(Optional.empty());
        when(userRepository.findByIdn(anyString())).thenReturn(Optional.of(existingByIdn));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = autoLinkingService.tryAutoLink("new-uuid", "784-1234-5678901-2", "SOP2");

        assertNotNull(result);
        assertEquals("new-uuid", result.getUaepassUuid());
        assertNotNull(result.getLinkedAt());
        verify(auditService).log(any(), eq("AUTO_LINK"), eq("USER"), any(), any(), any());
    }

    @Test
    @DisplayName("SOP3 citizen — auto-link by idn when uuid not found")
    void sop3AutoLinkByIdn() {
        User existingByIdn = User.builder()
                .id(UUID.randomUUID())
                .build();

        when(userRepository.findByUaepassUuid("citizen-uuid")).thenReturn(Optional.empty());
        when(userRepository.findByIdn(anyString())).thenReturn(Optional.of(existingByIdn));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = autoLinkingService.tryAutoLink("citizen-uuid", "784-9876-5432109-8", "SOP3");

        assertNotNull(result);
        assertEquals("citizen-uuid", result.getUaepassUuid());
        verify(auditService).log(any(), eq("AUTO_LINK"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SOP2 with blank idn — should return null (new user)")
    void sop2BlankIdnReturnsNull() {
        when(userRepository.findByUaepassUuid("uuid-no-idn")).thenReturn(Optional.empty());

        User result = autoLinkingService.tryAutoLink("uuid-no-idn", "", "SOP2");

        assertNull(result);
        verify(userRepository, never()).findByIdn(anyString());
    }

    @Test
    @DisplayName("SOP2 with idn but no idn match — should return null (new user)")
    void sop2IdnNoMatch() {
        when(userRepository.findByUaepassUuid("uuid-nomatch")).thenReturn(Optional.empty());
        when(userRepository.findByIdn(anyString())).thenReturn(Optional.empty());

        User result = autoLinkingService.tryAutoLink("uuid-nomatch", "784-0000-0000000-0", "SOP2");

        assertNull(result);
    }
}
