package com.yoursp.uaepass.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "uaepass_uuid", unique = true)
    private String uaepassUuid;

    @Column(name = "spuuid")
    private String spuuid;

    @Column(name = "idn", columnDefinition = "TEXT")
    private String idn;

    @Column(name = "email")
    private String email;

    @Column(name = "mobile", length = 30)
    private String mobile;

    @Column(name = "full_name_en", length = 500)
    private String fullNameEn;

    @Column(name = "full_name_ar", length = 500)
    private String fullNameAr;

    @Column(name = "first_name_en")
    private String firstNameEn;

    @Column(name = "last_name_en")
    private String lastNameEn;

    @Column(name = "nationality_en", length = 3, columnDefinition = "CHAR(3)")
    private String nationalityEn;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "user_type", length = 10)
    private String userType;

    @Column(name = "id_type", length = 20)
    private String idType;

    @Column(name = "acr", columnDefinition = "TEXT")
    private String acr;

    @Column(name = "linked_at")
    private OffsetDateTime linkedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null)
            createdAt = now;
        if (updatedAt == null)
            updatedAt = now;
        // linkedAt is set explicitly by linking services — NOT auto-set
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
