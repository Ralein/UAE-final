package com.yoursp.uaepass.modules.auth;

import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.repository.UserRepository;
import com.yoursp.uaepass.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Synchronizes user data from UAE PASS /userinfo response with local DB.
 * <ul>
 * <li>Creates new user if not found by uaepass_uuid</li>
 * <li>Auto-links by idn for SOP2/SOP3 users</li>
 * <li>Updates profile fields on every login</li>
 * <li>Encrypts idn before storing</li>
 * <li>Logs to audit_log</li>
 * </ul>
 *
 * IMPORTANT: userType mapping:
 * SOP1 = visitor (fewer attributes)
 * SOP2 = resident
 * SOP3 = citizen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final UserRepository userRepository;
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
        String spuuid = getStringValue(userInfo, "spuuid");

        if (uaepassUuid == null || uaepassUuid.isBlank()) {
            throw new IllegalArgumentException("UAE PASS uuid is required");
        }

        // 1. Try to find by uaepass_uuid
        Optional<User> existingUser = userRepository.findByUaepassUuid(uaepassUuid);

        User user;
        boolean isNewUser = false;

        if (existingUser.isPresent()) {
            user = existingUser.get();
            log.debug("Existing user found by uaepass_uuid: {}", user.getId());
        } else {
            // 2. For SOP2/SOP3 — try auto-link by idn
            if (idn != null && !idn.isBlank() && ("SOP2".equals(userType) || "SOP3".equals(userType))) {
                String encryptedIdn = CryptoUtil.encryptAES256(idn, idnEncryptionKey);
                Optional<User> linkedUser = userRepository.findByIdn(encryptedIdn);
                if (linkedUser.isPresent()) {
                    user = linkedUser.get();
                    user.setUaepassUuid(uaepassUuid);
                    log.info("Auto-linked user {} by idn (userType={})", user.getId(), userType);
                } else {
                    user = new User();
                    isNewUser = true;
                    user.setUaepassUuid(uaepassUuid);
                    log.info("Creating new user for uaepass_uuid: {} (userType={})", uaepassUuid, userType);
                }
            } else {
                // 3. Create new user
                user = new User();
                isNewUser = true;
                user.setUaepassUuid(uaepassUuid);
                log.info("Creating new user for uaepass_uuid: {} (userType={})", uaepassUuid, userType);
            }
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
                null, // IP set by controller
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
