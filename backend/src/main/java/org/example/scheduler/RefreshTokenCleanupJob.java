package org.example.scheduler;

import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.example.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long retentionDays;

    public RefreshTokenCleanupJob(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.refresh-token.cleanup-retention-days:7}") long retentionDays) {
        if (retentionDays < 0) {
            throw new IllegalStateException("app.refresh-token.cleanup-retention-days must not be negative");
        }
        this.refreshTokenRepository = refreshTokenRepository;
        this.retentionDays = retentionDays;
    }

    /** Deletes tokens that have been revoked or expired for longer than the retention period. */
    @Scheduled(cron = "${app.refresh-token.cleanup-cron:0 30 3 * * *}", zone = "UTC")
    @Transactional
    public void purgeStaleTokens() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        int deleted = refreshTokenRepository.deleteStale(cutoff);
        log.info("Refresh token cleanup removed {} row(s) older than {} day(s)", deleted, retentionDays);
    }
}