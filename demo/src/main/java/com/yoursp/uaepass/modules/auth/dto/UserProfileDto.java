package com.yoursp.uaepass.modules.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Public user profile DTO — NEVER contains tokens, idn, or sensitive data.
 */
@Getter
@Builder
public class UserProfileDto {

    private UUID id;
    private String fullNameEn;
    private String fullNameAr;
    private String email;
    private String mobile;
    private String userType;
    private String nationality;
    private OffsetDateTime linkedAt;
}
