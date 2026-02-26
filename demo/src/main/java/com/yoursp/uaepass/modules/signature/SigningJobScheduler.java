package com.yoursp.uaepass.modules.signature;

import com.yoursp.uaepass.repository.SigningJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Expires stale signing jobs that have been in INITIATED or AWAITING_USER
 * state for longer than the configured timeout (default: 60 minutes).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SigningJobScheduler {

    private final SigningJobRepository jobRepository;

    /**
     * Runs every 5 minutes. Sets status to EXPIRED for jobs past their expiry time.
     */
    @Scheduled(fixedRate = 300_000) // 5 minutes
    public void expireStaleJobs() {
        int count = jobRepository.expireStaleJobs(OffsetDateTime.now());
        if (count > 0) {
            log.info("Expired {} stale signing job(s)", count);
        }
    }
}
