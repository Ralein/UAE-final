package com.yoursp.uaepass.modules.auth;

import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.repository.UserRepository;
import com.yoursp.uaepass.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserSyncService userSyncService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userSyncService, "idnEncryptionKey", "test-encryption-key-for-idn!!");
    }

    @Test
    @DisplayName("SOP1 visitor — new user without idn")
    void syncNewSOP1User() {
        Map<String, Object> userInfo = createUserInfo("SOP1");
        userInfo.remove("idn"); // SOP1 visitors may not have idn

        when(userRepository.findByUaepassUuid(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        User result = userSyncService.syncUser(userInfo);

        assertNotNull(result);
        assertEquals("SOP1", result.getUserType());
        assertEquals("Test User", result.getFullNameEn());
        assertNull(result.getIdn()); // SOP1 has no idn

        verify(userRepository).save(any(User.class));
        verify(auditService).log(any(), eq("USER_REGISTERED"), eq("USER"), any(), any(), any());
    }

    @Test
    @DisplayName("SOP2 resident — new user with idn encryption")
    void syncNewSOP2User() {
        Map<String, Object> userInfo = createUserInfo("SOP2");

        when(userRepository.findByUaepassUuid(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByIdn(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        User result = userSyncService.syncUser(userInfo);

        assertNotNull(result);
        assertEquals("SOP2", result.getUserType());
        // idn should be encrypted (not plaintext)
        assertNotNull(result.getIdn());
        assertNotEquals("784-1234-5678901-2", result.getIdn());

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("SOP3 citizen — existing user updated on login")
    void syncExistingSOP3User() {
        UUID existingId = UUID.randomUUID();
        User existingUser = User.builder()
                .id(existingId)
                .uaepassUuid("uae-uuid-123")
                .fullNameEn("Old Name")
                .email("old@example.com")
                .build();

        Map<String, Object> userInfo = createUserInfo("SOP3");

        when(userRepository.findByUaepassUuid("uae-uuid-123")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userSyncService.syncUser(userInfo);

        assertEquals(existingId, result.getId());
        assertEquals("Test User", result.getFullNameEn()); // updated
        assertEquals("test@example.com", result.getEmail()); // updated
        assertEquals("SOP3", result.getUserType());

        verify(auditService).log(any(), eq("LOGIN"), eq("USER"), any(), any(), any());
    }

    @Test
    @DisplayName("SOP2 user — auto-link by idn when uaepass_uuid not found")
    void syncAutoLinkByIdn() {
        UUID existingId = UUID.randomUUID();
        User existingUser = User.builder()
                .id(existingId)
                .uaepassUuid("old-uaepass-uuid")
                .build();

        Map<String, Object> userInfo = createUserInfo("SOP2");
        userInfo.put("uuid", "new-uaepass-uuid"); // different UUID

        when(userRepository.findByUaepassUuid("new-uaepass-uuid")).thenReturn(Optional.empty());
        when(userRepository.findByIdn(anyString())).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userSyncService.syncUser(userInfo);

        assertEquals(existingId, result.getId());
        assertEquals("new-uaepass-uuid", result.getUaepassUuid()); // updated to new UUID
    }

    @Test
    @DisplayName("missing uuid should throw IllegalArgumentException")
    void syncWithMissingUuidShouldThrow() {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userType", "SOP1");

        assertThrows(IllegalArgumentException.class, () -> userSyncService.syncUser(userInfo));
    }

    // --- Helper ---

    private Map<String, Object> createUserInfo(String userType) {
        Map<String, Object> info = new HashMap<>();
        info.put("uuid", "uae-uuid-123");
        info.put("sub", "sub-123");
        info.put("userType", userType);
        info.put("fullnameEN", "Test User");
        info.put("fullnameAR", "مستخدم تجريبي");
        info.put("firstnameEN", "Test");
        info.put("lastnameEN", "User");
        info.put("nationalityEN", "ARE");
        info.put("gender", "Male");
        info.put("mobile", "+971501234567");
        info.put("email", "test@example.com");
        info.put("idn", "784-1234-5678901-2");
        info.put("idType", "RES");
        info.put("acr", "urn:safelayer:tws:policies:authentication:level:low");
        info.put("spuuid", "sp-uuid-456");
        return info;
    }
}
