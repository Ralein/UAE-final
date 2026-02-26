package com.yoursp.uaepass.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "session_token", unique = true, nullable = false, length = 512)
    private String sessionToken;

    @Column(name = "uaepass_token_ref", length = 256)
    private String uaepassTokenRef;

    @Column(name = "token_expires")
    private OffsetDateTime tokenExpires;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_active")
    private OffsetDateTime lastActive;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null)
            createdAt = now;
        if (lastActive == null)
            lastActive = now;
    }
}
