package com.yoursp.uaepass.modules.auth;

import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.linking.AutoLinkingService;
import com.yoursp.uaepass.repository.UserRepository;
import com.yoursp.uaepass.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Synchronizes user data from UAE PASS /userinfo response with local DB.
 * <ul>
 * <li>Delegates auto-linking to {@link AutoLinkingService}</li>
 * <li>Creates new user if auto-link finds no match</li>
 * <li>Updates profile fields on every login</li>
 * <li>Encrypts idn before storing</li>
 * <li>Logs to audit_log</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final UserRepository userRepository;
    private final AutoLinkingService autoLinkingService;
    private final AuditService auditService;

    @Value("${idn.encryption-key}")
    private String idnEncryptionKey;

    /**
     * Sync user info from UAE PASS into local database.
     *
     * @param userInfo the Map returned from /userinfo endpoint
     * @return the saved User entity
     */
    @Transactional
    public User syncUser(Map<String, Object> userInfo) {
        String uaepassUuid = getStringValue(userInfo, "uuid");
        String idn = getStringValue(userInfo, "idn");
        String userType = getStringValue(userInfo, "userType");

        if (uaepassUuid == null || uaepassUuid.isBlank()) {
            throw new IllegalArgumentException("UAE PASS uuid is required");
        }

        // Delegate auto-linking to AutoLinkingService
        User user = autoLinkingService.tryAutoLink(uaepassUuid, idn, userType);

        boolean isNewUser = false;

        if (user == null) {
            // No existing user found — create new
            user = new User();
            isNewUser = true;
            user.setUaepassUuid(uaepassUuid);
            user.setLinkedAt(OffsetDateTime.now());
            log.info("Creating new user for uaepass_uuid: {} (userType={})", uaepassUuid, userType);
        }

        // Update profile fields on every login
        updateUserFields(user, userInfo, idn);

        User savedUser = userRepository.save(user);

        // Audit log
        auditService.log(
                savedUser.getId(),
                isNewUser ? "USER_REGISTERED" : "LOGIN",
                "USER",
                savedUser.getId().toString(),
                null,
                Map.of("userType", userType != null ? userType : "unknown",
                        "isNewUser", isNewUser));

        return savedUser;
    }

    private void updateUserFields(User user, Map<String, Object> userInfo, String rawIdn) {
        user.setFullNameEn(getStringValue(userInfo, "fullnameEN"));
        user.setFullNameAr(getStringValue(userInfo, "fullnameAR"));
        user.setFirstNameEn(getStringValue(userInfo, "firstnameEN"));
        user.setLastNameEn(getStringValue(userInfo, "lastnameEN"));
        user.setEmail(getStringValue(userInfo, "email"));
        user.setMobile(getStringValue(userInfo, "mobile"));
        user.setNationalityEn(getStringValue(userInfo, "nationalityEN"));
        user.setGender(getStringValue(userInfo, "gender"));
        user.setUserType(getStringValue(userInfo, "userType"));
        user.setIdType(getStringValue(userInfo, "idType"));
        user.setAcr(getStringValue(userInfo, "acr"));
        user.setSpuuid(getStringValue(userInfo, "spuuid"));

        // Encrypt idn before storing — NEVER store plaintext
        if (rawIdn != null && !rawIdn.isBlank()) {
            user.setIdn(CryptoUtil.encryptAES256(rawIdn, idnEncryptionKey));
        }
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
