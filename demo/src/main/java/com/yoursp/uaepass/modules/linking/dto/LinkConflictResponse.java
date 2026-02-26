package com.yoursp.uaepass.modules.linking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Returned with 409 Conflict when linking violates the one-to-one constraint.
 */
@Getter
@AllArgsConstructor
public class LinkConflictResponse {

    /**
     * Possible values:
     * <ul>
     * <li>{@code ALREADY_LINKED_TO_OTHER_USER} — the uaepass_uuid is already linked
     * to a different SP user</li>
     * <li>{@code USER_ALREADY_HAS_LINK} — the SP user already has a different
     * uaepass_uuid linked</li>
     * </ul>
     */
    private String error;
    private String message;
}
