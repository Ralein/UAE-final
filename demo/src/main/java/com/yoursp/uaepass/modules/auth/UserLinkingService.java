package com.yoursp.uaepass.modules.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Stub service for user account linking — full implementation in Phase 3.
 * <ul>
 * <li>autoLink: automatically link a UAE PASS account to an SP user via
 * idn</li>
 * <li>linkManually: link by explicit SP user ID</li>
 * <li>unlinkAccount: remove UAE PASS link from SP user</li>
 * </ul>
 */
@Slf4j
@Service
public class UserLinkingService {

    /**
     * Attempt automatic linking of a UAE PASS account to an existing SP user
     * based on the Emirates ID (idn).
     *
     * @param uaepassUuid the UAE PASS UUID
     * @param idn         the Emirates ID (encrypted)
     * @param userType    SOP1/SOP2/SOP3
     * @return true if auto-linking succeeded
     */
    public boolean autoLink(String uaepassUuid, String idn, String userType) {
        // TODO: Full implementation in Phase 3
        log.info("AutoLink stub called: uaepassUuid={}, userType={}", uaepassUuid, userType);
        return false;
    }

    /**
     * Manually link a UAE PASS account to a specific SP user.
     *
     * @param spUserId    the SP user's UUID
     * @param uaepassUuid the UAE PASS UUID to link
     */
    public void linkManually(UUID spUserId, String uaepassUuid) {
        // TODO: Full implementation in Phase 3
        log.info("ManualLink stub called: spUserId={}, uaepassUuid={}", spUserId, uaepassUuid);
    }

    /**
     * Remove the UAE PASS link from an SP user.
     *
     * @param spUserId the SP user's UUID
     */
    public void unlinkAccount(UUID spUserId) {
        // TODO: Full implementation in Phase 3
        log.info("UnlinkAccount stub called: spUserId={}", spUserId);
    }
}
