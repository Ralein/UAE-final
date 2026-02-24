package com.yoursp.uaepass.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "signing_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SigningJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "signer_process_id", unique = true)
    private String signerProcessId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "signing_type", length = 20)
    private String signingType;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "document_count")
    private Integer documentCount;

    @Column(name = "sign_identity_id")
    private String signIdentityId;

    /** JSONB column — stored as raw JSON string, parsed manually when needed. */
    @Column(name = "documents", columnDefinition = "JSONB")
    private String documents;

    @Column(name = "finish_callback_url", columnDefinition = "TEXT")
    private String finishCallbackUrl;

    @Column(name = "callback_status", length = 20)
    private String callbackStatus;

    @Column(name = "ltv_applied")
    private Boolean ltvApplied;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "initiated_at")
    private OffsetDateTime initiatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = OffsetDateTime.now();
        if (documentCount == null)
            documentCount = 1;
        if (ltvApplied == null)
            ltvApplied = false;
    }
}
