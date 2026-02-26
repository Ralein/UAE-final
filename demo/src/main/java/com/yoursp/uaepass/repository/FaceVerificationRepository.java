package com.yoursp.uaepass.repository;

import com.yoursp.uaepass.model.entity.FaceVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FaceVerificationRepository extends JpaRepository<FaceVerification, UUID> {

    /**
     * Find the most recent VERIFIED face verification for a user within the time
     * window.
     */
    @Query("SELECT fv FROM FaceVerification fv " +
            "WHERE fv.userId = :userId AND fv.status = 'VERIFIED' AND fv.uuidMatch = true " +
            "AND fv.verifiedAt > :cutoff " +
            "ORDER BY fv.verifiedAt DESC LIMIT 1")
    Optional<FaceVerification> findRecentVerified(UUID userId, OffsetDateTime cutoff);
}
