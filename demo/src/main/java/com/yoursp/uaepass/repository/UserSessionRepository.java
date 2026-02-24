package com.yoursp.uaepass.repository;

import com.yoursp.uaepass.model.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findBySessionToken(String sessionToken);

    @Modifying
    @Transactional
    void deleteByUserId(UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.lastActive = CURRENT_TIMESTAMP WHERE s.id = :sessionId")
    void updateLastActive(UUID sessionId);
}
