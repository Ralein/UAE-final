package com.yoursp.uaepass.repository;

import com.yoursp.uaepass.model.entity.SigningJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SigningJobRepository extends JpaRepository<SigningJob, UUID> {

    Optional<SigningJob> findBySignerProcessId(String signerProcessId);

    List<SigningJob> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SigningJob> findAllByUserId(UUID userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM SigningJob j WHERE j.userId = :userId")
    int deleteAllByUserId(UUID userId);

    long countByStatus(String status);

    long countByStatusAndLtvApplied(String status, boolean ltvApplied);

    @Modifying
    @Transactional
    @Query("UPDATE SigningJob j SET j.status = 'EXPIRED' " +
            "WHERE j.status IN ('INITIATED', 'AWAITING_USER') AND j.expiresAt < :now")
    int expireStaleJobs(OffsetDateTime now);
}
