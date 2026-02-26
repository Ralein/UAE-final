package com.yoursp.uaepass.modules.linking;

import com.yoursp.uaepass.model.entity.User;
import com.yoursp.uaepass.modules.linking.exception.LinkConflictException;
import com.yoursp.uaepass.repository.UserRepository;
import com.yoursp.uaepass.repository.UserSessionRepository;
import com.yoursp.uaepass.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manual linking and unlinking of UAE PASS accounts.
 * Enforces strict one-to-one mapping:
 * <ul>
 * <li>One SP user can be linked to exactly one uaepass_uuid</li>
 * <li>One uaepass_uuid can be linked to exactly one SP user</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ManualLinkingService {

        private final UserRepository userRepository;
        private final UserSessionRepository sessionRepository;
        private final AuditService auditService;

        /**
         * Manually link a UAE PASS identity to an SP user.
         *
         * @param spUserId    the SP user's UUID
         * @param uaepassUuid the UAE PASS UUID to link
         * @throws LinkConflictException if one-to-one constraint would be violated
         */
        @Transactional
        public void linkBySession(UUID spUserId, String uaepassUuid) {
                // Conflict check 1: Is this uaepass_uuid already linked to another SP user?
                Optional<User> existingByUuid = userRepository.findByUaepassUuid(uaepassUuid);
                if (existingByUuid.isPresent() && !existingByUuid.get().getId().equals(spUserId)) {
                        log.warn("Link conflict: uaepass_uuid {} already linked to user {}",
                                        uaepassUuid, existingByUuid.get().getId());
                        throw new LinkConflictException(
                                        "ALREADY_LINKED_TO_OTHER_USER",
                                        "This UAE PASS account is already linked to another user");
                }

                // Conflict check 2: Does this SP user already have a different uaepass_uuid?
                User spUser = userRepository.findById(spUserId)
                                .orElseThrow(() -> new IllegalArgumentException("User not found: " + spUserId));

                if (spUser.getUaepassUuid() != null && !spUser.getUaepassUuid().equals(uaepassUuid)) {
                        log.warn("Link conflict: user {} already linked to uaepass_uuid {}",
                                        spUserId, spUser.getUaepassUuid());
                        throw new LinkConflictException(
                                        "USER_ALREADY_HAS_LINK",
                                        "This user is already linked to a different UAE PASS account. Unlink first.");
                }

                // Perform the link
                spUser.setUaepassUuid(uaepassUuid);
                spUser.setLinkedAt(OffsetDateTime.now());
                userRepository.save(spUser);

                log.info("Manually linked user {} to uaepass_uuid {}", spUserId, uaepassUuid);

                auditService.log(
                                spUserId,
                                "LINK",
                                "USER",
                                spUserId.toString(),
                                null,
                                Map.of("uaepassUuid", uaepassUuid,
                                                "linkedBy", "MANUAL"));
        }

        /**
         * Remove the UAE PASS link from an SP user.
         * <ul>
         * <li>Sets uaepass_uuid = null (never deletes the user)</li>
         * <li>Sets linkedAt = null</li>
         * <li>Invalidates ALL sessions for this user</li>
         * <li>Logs audit event</li>
         * </ul>
         *
         * @param spUserId the SP user's UUID
         */
        @Transactional
        public void unlinkUser(UUID spUserId) {
                User user = userRepository.findById(spUserId)
                                .orElseThrow(() -> new IllegalArgumentException("User not found: " + spUserId));

                String previousUuid = user.getUaepassUuid();

                if (previousUuid == null) {
                        log.debug("User {} is not linked — nothing to unlink", spUserId);
                        return;
                }

                // Null out link fields — NEVER delete the user
                user.setUaepassUuid(null);
                user.setLinkedAt(null);
                userRepository.save(user);

                // Invalidate ALL sessions for this user
                sessionRepository.deleteByUserId(spUserId);

                log.info("Unlinked user {} (previous uaepass_uuid={})", spUserId, previousUuid);

                auditService.log(
                                spUserId,
                                "UNLINK",
                                "USER",
                                spUserId.toString(),
                                null,
                                Map.of("previousUuid", previousUuid));
        }
}
