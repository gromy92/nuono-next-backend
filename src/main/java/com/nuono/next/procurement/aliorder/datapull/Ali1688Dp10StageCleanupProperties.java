package com.nuono.next.procurement.aliorder.datapull;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Small bounded batches for live-generation cleanup and failed-task retention. */
@ConfigurationProperties(prefix = "nuono.data-pull.dp10-stage-cleanup")
public class Ali1688Dp10StageCleanupProperties {
    private static final int MAX_BATCH_SIZE = 500;

    private int batchSize = 100;
    private long failedTaskGraceSeconds = Duration.ofDays(7).toSeconds();
    private long retentionRunIntervalSeconds = Duration.ofHours(1).toSeconds();

    public void validate() {
        if (batchSize <= 0 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalStateException("DP-10 cleanup batch size must be between 1 and 500");
        }
        if (failedTaskGraceSeconds < Duration.ofDays(7).toSeconds()) {
            throw new IllegalStateException("DP-10 failed-task cleanup grace must be at least 7 days");
        }
        if (retentionRunIntervalSeconds <= 0L) {
            throw new IllegalStateException("DP-10 cleanup interval must be positive");
        }
    }

    public Duration failedTaskGrace() { return Duration.ofSeconds(failedTaskGraceSeconds); }
    public Duration retentionRunInterval() {
        return Duration.ofSeconds(retentionRunIntervalSeconds);
    }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { batchSize = value; }
    public long getFailedTaskGraceSeconds() { return failedTaskGraceSeconds; }
    public void setFailedTaskGraceSeconds(long value) { failedTaskGraceSeconds = value; }
    public long getRetentionRunIntervalSeconds() { return retentionRunIntervalSeconds; }
    public void setRetentionRunIntervalSeconds(long value) {
        retentionRunIntervalSeconds = value;
    }
}
