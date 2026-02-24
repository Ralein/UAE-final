package com.yoursp.uaepass.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "eseal_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EsealJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "seal_type", length = 10)
    private String sealType;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "input_key", columnDefinition = "TEXT")
    private String inputKey;

    @Column(name = "output_key", columnDefinition = "TEXT")
    private String outputKey;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = OffsetDateTime.now();
    }
}
