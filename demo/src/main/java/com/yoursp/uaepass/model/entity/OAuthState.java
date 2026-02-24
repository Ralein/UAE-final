package com.yoursp.uaepass.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "oauth_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthState {

    @Id
    @Column(name = "state", length = 256)
    private String state;

    @Column(name = "flow_type", length = 50)
    private String flowType;

    @Column(name = "redirect_after", columnDefinition = "TEXT")
    private String redirectAfter;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "used")
    private Boolean used;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = OffsetDateTime.now();
        if (used == null)
            used = false;
    }
}
