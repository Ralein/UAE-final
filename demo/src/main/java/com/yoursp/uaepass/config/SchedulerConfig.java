package com.yoursp.uaepass.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled methods (SigningJobScheduler).
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
