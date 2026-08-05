package com.nuono.next.datapull.orchestration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Technical runtime limits; none of these values disable a business DP or truncate its scope. */
@ConfigurationProperties(prefix = "nuono.data-pull.runtime")
public class DataPullRuntimeProperties {

    static final long ADVANCE_BUDGET_SECONDS = 90L;
    static final long JOB_BUDGET_SECONDS = 75L;
    static final long TRANSITION_BUDGET_SECONDS = 10L;
    static final long DEADLINE_RESERVE_SECONDS = 5L;
    static final long SCHEDULER_PHASE_BUDGET_SECONDS = 10L;
    static final long MINIMUM_LEADER_LEASE_SECONDS =
            ADVANCE_BUDGET_SECONDS + SCHEDULER_PHASE_BUDGET_SECONDS;
    public static final int DATABASE_TRANSACTION_TIMEOUT_SECONDS = 10;
    static final long MINIMUM_LEASE_MULTIPLIER = 3L;

    private long schedulerInitialDelayMs = 30_000L;
    private long schedulerFixedDelayMs = 15_000L;
    private int workerCount = 4;
    private long leaseSeconds = 300L;
    private long leaderLeaseSeconds = 120L;
    private int maximumClaimsPerTick = 4;
    private long backoffBaseSeconds = 60L;
    private long backoffMaximumSeconds = 3_600L;
    private double backoffJitterRatio = 0.20d;

    public void validate() {
        if (schedulerInitialDelayMs < 0L || schedulerFixedDelayMs <= 0L) {
            throw new IllegalStateException("DP scheduler delays are invalid");
        }
        if (workerCount <= 0 || maximumClaimsPerTick <= 0) {
            throw new IllegalStateException("DP runtime concurrency must be positive");
        }
        if (maximumClaimsPerTick > workerCount) {
            throw new IllegalStateException("maximum claims per tick must not exceed worker count");
        }
        if (leaseSeconds <= 0L || leaderLeaseSeconds <= 0L
                || backoffBaseSeconds <= 0L || backoffMaximumSeconds <= 0L) {
            throw new IllegalStateException("DP runtime durations must be positive");
        }
        if (leaseSeconds < ADVANCE_BUDGET_SECONDS * MINIMUM_LEASE_MULTIPLIER) {
            throw new IllegalStateException("DP lease must cover three advance budgets");
        }
        long phaseSlackMs = SCHEDULER_PHASE_BUDGET_SECONDS * 2_000L;
        if (leaderLeaseSeconds > Integer.MAX_VALUE
                || schedulerFixedDelayMs > Long.MAX_VALUE - phaseSlackMs) {
            throw new IllegalStateException("DP leader lease configuration exceeds its bound");
        }
        long renewalGapMs = schedulerFixedDelayMs + phaseSlackMs;
        long renewalGapSeconds = renewalGapMs / 1_000L
                + (renewalGapMs % 1_000L == 0L ? 0L : 1L);
        if (leaderLeaseSeconds < MINIMUM_LEADER_LEASE_SECONDS
                || leaderLeaseSeconds < renewalGapSeconds) {
            throw new IllegalStateException("DP leader lease does not cover its renewal gap");
        }
        if (JOB_BUDGET_SECONDS + TRANSITION_BUDGET_SECONDS + DEADLINE_RESERVE_SECONDS
                != ADVANCE_BUDGET_SECONDS) {
            throw new IllegalStateException("DP advance deadline partition is invalid");
        }
        if (backoffBaseSeconds > backoffMaximumSeconds) {
            throw new IllegalStateException("DP backoff base must not exceed its maximum");
        }
        if (!Double.isFinite(backoffJitterRatio)
                || backoffJitterRatio < 0.0d
                || backoffJitterRatio > 1.0d) {
            throw new IllegalStateException("DP backoff jitter ratio must be between zero and one");
        }
    }

    public Duration leaseDuration() { return Duration.ofSeconds(leaseSeconds); }
    public Duration leaderLeaseDuration() { return Duration.ofSeconds(leaderLeaseSeconds); }
    public Duration backoffBaseDelay() { return Duration.ofSeconds(backoffBaseSeconds); }
    public Duration backoffMaximumDelay() { return Duration.ofSeconds(backoffMaximumSeconds); }

    public long getSchedulerInitialDelayMs() { return schedulerInitialDelayMs; }
    public void setSchedulerInitialDelayMs(long value) { this.schedulerInitialDelayMs = value; }
    public long getSchedulerFixedDelayMs() { return schedulerFixedDelayMs; }
    public void setSchedulerFixedDelayMs(long value) { this.schedulerFixedDelayMs = value; }
    public int getWorkerCount() { return workerCount; }
    public void setWorkerCount(int workerCount) { this.workerCount = workerCount; }
    public long getLeaseSeconds() { return leaseSeconds; }
    public void setLeaseSeconds(long leaseSeconds) { this.leaseSeconds = leaseSeconds; }
    public long getLeaderLeaseSeconds() { return leaderLeaseSeconds; }
    public void setLeaderLeaseSeconds(long value) { this.leaderLeaseSeconds = value; }
    public int getMaximumClaimsPerTick() { return maximumClaimsPerTick; }
    public void setMaximumClaimsPerTick(int value) { this.maximumClaimsPerTick = value; }
    public long getBackoffBaseSeconds() { return backoffBaseSeconds; }
    public void setBackoffBaseSeconds(long value) { this.backoffBaseSeconds = value; }
    public long getBackoffMaximumSeconds() { return backoffMaximumSeconds; }
    public void setBackoffMaximumSeconds(long value) { this.backoffMaximumSeconds = value; }
    public double getBackoffJitterRatio() { return backoffJitterRatio; }
    public void setBackoffJitterRatio(double value) { this.backoffJitterRatio = value; }
}
