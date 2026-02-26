package com.yoursp.uaepass.modules.auth;

import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.linking.AutoLinkingService;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class UserSyncServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AutoLinkingService autoLinkingService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserSyncService userSyncService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userSyncService, "idnEncryptionKey", "test-encryption-key-for-idn!!");
    }

    @Test
    @DisplayName("New user — autoLink returns null, creates new user")
    void syncNewUser() {
        Map<String, Object> userInfo = createUserInfo("SOP1");

        when(autoLinkingService.tryAutoLink(any(), any(), any())).thenReturn(null);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        User result = userSyncService.syncUser(userInfo);

        assertNotNull(result);
        assertEquals("SOP1", result.getUserType());
        verify(auditService).log(any(), eq("USER_REGISTERED"), eq("USER"), any(), any(), any());
    }

    @Test
    @DisplayName("Existing user — autoLink returns existing user, updates fields")
    void syncExistingUser() {
        UUID existingId = UUID.randomUUID();
        User existingUser = User.builder()
                .id(existingId)
                .uaepassUuid("uae-uuid-123")
                .fullNameEn("Old Name")
                .build();

        Map<String, Object> userInfo = createUserInfo("SOP3");

        when(autoLinkingService.tryAutoLink(any(), any(), any())).thenReturn(existingUser);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userSyncService.syncUser(userInfo);

        assertEquals(existingId, result.getId());
        assertEquals("Test User", result.getFullNameEn()); // updated
        verify(auditService).log(any(), eq("LOGIN"), eq("USER"), any(), any(), any());
    }

    @Test
    @DisplayName("Missing uuid should throw IllegalArgumentException")
    void syncWithMissingUuidShouldThrow() {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userType", "SOP1");

        assertThrows(IllegalArgumentException.class, () -> userSyncService.syncUser(userInfo));
    }

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
