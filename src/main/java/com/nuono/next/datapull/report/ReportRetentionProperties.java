package com.nuono.next.datapull.report;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounded retention settings; terminal-task grace is measured on the UTC timeline. */
@ConfigurationProperties(prefix = "nuono.data-pull.report.retention")
public class ReportRetentionProperties {
    private static final int MAXIMUM_BATCH_SIZE = 1_000;

    private long graceSeconds = Duration.ofDays(7).toSeconds();
    private long failedGraceSeconds = Duration.ofDays(30).toSeconds();
    private long runIntervalSeconds = Duration.ofHours(1).toSeconds();
    private long catchUpIntervalSeconds = 15L;
    private int batchSize = 100;
    private int maximumStatementsPerRun = 60;
    private long maximumRunMillis = 8_000L;

    public void validate() {
        if (graceSeconds <= 0L || failedGraceSeconds < graceSeconds
                || runIntervalSeconds <= 0L
                || catchUpIntervalSeconds <= 0L
                || catchUpIntervalSeconds > runIntervalSeconds) {
            throw new IllegalStateException("report retention durations must be positive");
        }
        if (batchSize <= 0 || batchSize > MAXIMUM_BATCH_SIZE) {
            throw new IllegalStateException("report retention batch size must be between 1 and 1000");
        }
        if (maximumStatementsPerRun < 10 || maximumStatementsPerRun > 1_000) {
            throw new IllegalStateException(
                    "report retention maximum statements must be between 10 and 1000"
            );
        }
        if (maximumRunMillis <= 0L || maximumRunMillis > 9_000L) {
            throw new IllegalStateException(
                    "report retention maximum run time must be between 1 and 9000 ms"
            );
        }
    }

    public Duration grace() { return Duration.ofSeconds(graceSeconds); }
    public Duration failedGrace() { return Duration.ofSeconds(failedGraceSeconds); }
    public Duration runInterval() { return Duration.ofSeconds(runIntervalSeconds); }
    public Duration catchUpInterval() { return Duration.ofSeconds(catchUpIntervalSeconds); }

    public long getGraceSeconds() { return graceSeconds; }
    public void setGraceSeconds(long value) { this.graceSeconds = value; }
    public long getFailedGraceSeconds() { return failedGraceSeconds; }
    public void setFailedGraceSeconds(long value) { this.failedGraceSeconds = value; }
    public long getRunIntervalSeconds() { return runIntervalSeconds; }
    public void setRunIntervalSeconds(long value) { this.runIntervalSeconds = value; }
    public long getCatchUpIntervalSeconds() { return catchUpIntervalSeconds; }
    public void setCatchUpIntervalSeconds(long value) { this.catchUpIntervalSeconds = value; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { this.batchSize = value; }
    public int getMaximumStatementsPerRun() { return maximumStatementsPerRun; }
    public void setMaximumStatementsPerRun(int value) { this.maximumStatementsPerRun = value; }
    public long getMaximumRunMillis() { return maximumRunMillis; }
    public void setMaximumRunMillis(long value) { this.maximumRunMillis = value; }
}
