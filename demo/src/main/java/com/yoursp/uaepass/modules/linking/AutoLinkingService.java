package com.yoursp.uaepass.modules.linking;

import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.auth.CryptoUtil;
import com.yoursp.uaepass.repository.UserRepository;
import com.yoursp.uaepass.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Automatic linking of UAE PASS accounts to existing SP users.
 * Called from {@link com.yoursp.uaepass.modules.auth.UserSyncService} during
 * login.
 *
 * <h3>Strategy:</h3>
 * <ol>
 * <li>Look up by uaepass_uuid — if found, user is already linked</li>
 * <li>For SOP2/SOP3: encrypt incoming idn → look up by encrypted idn →
 * auto-link</li>
 * <li>SOP1 visitors: ONLY uuid-based lookup (no idn available)</li>
 * <li>If nothing matches → return null (caller creates new user)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoLinkingService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    @Value("${idn.encryption-key}")
    private String idnEncryptionKey;

    /**
     * Attempt to find an existing SP user for the incoming UAE PASS identity.
     *
     * @param incomingUuid the UAE PASS UUID (immutable, always present)
     * @param incomingIdn  the Emirates ID (present for SOP2/SOP3, null for SOP1)
     * @param userType     SOP1/SOP2/SOP3
     * @return the matched User if found, or null if no match (caller creates new
     *         user)
     */
    @Transactional
    public User tryAutoLink(String incomingUuid, String incomingIdn, String userType) {
        // Step 1: Direct lookup by uaepass_uuid
        Optional<User> byUuid = userRepository.findByUaepassUuid(incomingUuid);
        if (byUuid.isPresent()) {
            log.debug("User already linked by uaepass_uuid: {}", byUuid.get().getId());
            return byUuid.get();
        }

        // Step 2: SOP2/SOP3 — attempt auto-link by idn
        if (incomingIdn != null && !incomingIdn.isBlank()
                && ("SOP2".equals(userType) || "SOP3".equals(userType))) {

            String encryptedIdn = CryptoUtil.encryptAES256(incomingIdn, idnEncryptionKey);
            Optional<User> byIdn = userRepository.findByIdn(encryptedIdn);

            if (byIdn.isPresent()) {
                User user = byIdn.get();
                user.setUaepassUuid(incomingUuid);
                user.setLinkedAt(OffsetDateTime.now());
                User saved = userRepository.save(user);

                log.info("Auto-linked user {} by idn (userType={})", saved.getId(), userType);

                auditService.log(
                        saved.getId(),
                        "AUTO_LINK",
                        "USER",
                        saved.getId().toString(),
                        null,
                        Map.of("matchedBy", "IDN",
                                "userType", userType,
                                "uaepassUuid", incomingUuid));

                return saved;
            }
        }

        // Step 3: No match found
        // SOP1 visitors can ONLY be matched by uuid — never by email/mobile/idn
        log.debug("No existing user found for uuid={} (userType={})", incomingUuid, userType);
        return null;
    }
}
