package com.yoursp.uaepass.modules.linking;

import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.linking.exception.LinkConflictException;
import com.yoursp.uaepass.repository.UserRepository;
import com.yoursp.uaepass.repository.UserSessionRepository;
import com.yoursp.uaepass.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ManualLinkingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository sessionRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ManualLinkingService manualLinkingService;

    @Test
    @DisplayName("Successful manual link — user has no existing link")
    void linkSuccess() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();

        when(userRepository.findByUaepassUuid("uae-uuid")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        manualLinkingService.linkBySession(userId, "uae-uuid");

        assertEquals("uae-uuid", user.getUaepassUuid());
        assertNotNull(user.getLinkedAt());
        verify(auditService).log(eq(userId), eq("LINK"), eq("USER"), any(), any(), any());
    }

    @Test
    @DisplayName("Conflict: uaepass_uuid already linked to another SP user")
    void conflictUuidAlreadyLinkedToOtherUser() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        User otherUser = User.builder().id(otherUserId).uaepassUuid("uae-uuid").build();

        when(userRepository.findByUaepassUuid("uae-uuid")).thenReturn(Optional.of(otherUser));

        LinkConflictException ex = assertThrows(
                LinkConflictException.class,
                () -> manualLinkingService.linkBySession(userId, "uae-uuid"));

        assertEquals("ALREADY_LINKED_TO_OTHER_USER", ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Conflict: SP user already has different uaepass_uuid")
    void conflictUserAlreadyHasLink() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .uaepassUuid("existing-uuid")
                .build();

        when(userRepository.findByUaepassUuid("new-uuid")).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        LinkConflictException ex = assertThrows(
                LinkConflictException.class,
                () -> manualLinkingService.linkBySession(userId, "new-uuid"));

        assertEquals("USER_ALREADY_HAS_LINK", ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Re-linking same uaepass_uuid to same user — should succeed (idempotent)")
    void relinkSameUuidSameUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .uaepassUuid("same-uuid")
                .build();

        when(userRepository.findByUaepassUuid("same-uuid")).thenReturn(Optional.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> manualLinkingService.linkBySession(userId, "same-uuid"));
    }

    @Test
    @DisplayName("Unlink — nulls uuid, invalidates sessions, logs audit")
    void unlinkSuccess() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .uaepassUuid("linked-uuid")
                .linkedAt(OffsetDateTime.now())
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        manualLinkingService.unlinkUser(userId);

        assertNull(user.getUaepassUuid());
        assertNull(user.getLinkedAt());
        verify(sessionRepository).deleteByUserId(userId);
        verify(auditService).log(eq(userId), eq("UNLINK"), eq("USER"), any(), any(), any());
    }

    @Test
    @DisplayName("Unlink already unlinked user — should be a no-op")
    void unlinkAlreadyUnlinked() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        manualLinkingService.unlinkUser(userId);

        // Should NOT invalidate sessions or log audit
        verify(sessionRepository, never()).deleteByUserId(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any());
    }
}
