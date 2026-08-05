package com.nuono.next.datapull.runtime;

import java.time.Duration;

/**
 * Result of one bounded operation advance.
 *
 * <p>An Implementation returns after at most one external call or one short
 * local transaction. RUNNING is therefore never a valid returned state.</p>
 */
public final class AdvanceResult {

    private static final Duration DEFAULT_AUTH_POLL_DELAY = Duration.ofMinutes(5);
    private static final int PROVIDER_CHANNEL_MAX_LENGTH = 64;

    private final TaskState nextState;
    private final String stepCode;
    private final String remoteHandle;
    private final String checkpoint;
    private final Duration retryAfter;
    private final String sanitizedCode;
    private final RiskShareLevel backoffShareLevel;
    private final String backoffProviderChannel;

    private AdvanceResult(
            TaskState nextState,
            String stepCode,
            String remoteHandle,
            String checkpoint,
            Duration retryAfter,
            String sanitizedCode,
            RiskShareLevel backoffShareLevel,
            String backoffProviderChannel
    ) {
        this.nextState = nextState;
        this.stepCode = stepCode;
        this.remoteHandle = remoteHandle;
        this.checkpoint = checkpoint;
        this.retryAfter = retryAfter;
        this.sanitizedCode = sanitizedCode;
        this.backoffShareLevel = backoffShareLevel;
        this.backoffProviderChannel = backoffProviderChannel;
    }

    public static AdvanceResult queued(String checkpoint) {
        return queued(null, null, checkpoint);
    }

    public static AdvanceResult queued(String stepCode, String remoteHandle, String checkpoint) {
        return new AdvanceResult(
                TaskState.QUEUED,
                stepCode,
                remoteHandle,
                checkpoint,
                null,
                null,
                null,
                null
        );
    }

    public static AdvanceResult waitingRemote(
            String checkpoint,
            Duration retryAfter,
            String sanitizedCode
    ) {
        return waitingRemote(null, null, checkpoint, retryAfter, sanitizedCode);
    }

    public static AdvanceResult waitingRemote(
            String stepCode,
            String remoteHandle,
            String checkpoint,
            Duration retryAfter,
            String sanitizedCode
    ) {
        return waiting(
                TaskState.WAITING_REMOTE,
                stepCode,
                remoteHandle,
                checkpoint,
                retryAfter,
                sanitizedCode,
                null,
                null
        );
    }

    public static AdvanceResult waitingBackoff(
            String checkpoint,
            Duration retryAfter,
            String sanitizedCode
    ) {
        return waitingBackoff(
                null,
                null,
                checkpoint,
                retryAfter,
                sanitizedCode,
                RiskShareLevel.EXACT
        );
    }

    public static AdvanceResult waitingBackoff(
            String checkpoint,
            Duration retryAfter,
            String sanitizedCode,
            RiskShareLevel shareLevel
    ) {
        return waitingBackoff(null, null, checkpoint, retryAfter, sanitizedCode, shareLevel);
    }

    public static AdvanceResult waitingBackoff(
            String stepCode,
            String remoteHandle,
            String checkpoint,
            Duration retryAfter,
            String sanitizedCode
    ) {
        return waitingBackoff(
                stepCode,
                remoteHandle,
                checkpoint,
                retryAfter,
                sanitizedCode,
                RiskShareLevel.EXACT
        );
    }

    public static AdvanceResult waitingBackoff(
            String stepCode,
            String remoteHandle,
            String checkpoint,
            Duration retryAfter,
            String sanitizedCode,
            RiskShareLevel shareLevel
    ) {
        return waiting(
                TaskState.WAITING_BACKOFF,
                stepCode,
                remoteHandle,
                checkpoint,
                retryAfter,
                sanitizedCode,
                java.util.Objects.requireNonNull(shareLevel, "shareLevel"),
                null
        );
    }

    /**
     * A routing job may call more than one provider while retaining one immutable task channel.
     * This override is consumed only by the transactional backoff commit.
     */
    public static AdvanceResult waitingBackoffForProvider(
            String providerChannel,
            String stepCode,
            String remoteHandle,
            String checkpoint,
            Duration retryAfter,
            String sanitizedCode,
            RiskShareLevel shareLevel
    ) {
        String channel = java.util.Objects.requireNonNull(providerChannel, "providerChannel");
        if (channel.isEmpty()
                || !channel.equals(channel.trim())
                || channel.length() > PROVIDER_CHANNEL_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "providerChannel must be a stable identity of at most 64 characters"
            );
        }
        return waiting(
                TaskState.WAITING_BACKOFF,
                stepCode,
                remoteHandle,
                checkpoint,
                retryAfter,
                sanitizedCode,
                java.util.Objects.requireNonNull(shareLevel, "shareLevel"),
                channel
        );
    }

    public static AdvanceResult waitingAuth(String checkpoint, String sanitizedCode) {
        return waitingAuth(null, null, checkpoint, DEFAULT_AUTH_POLL_DELAY, sanitizedCode);
    }

    public static AdvanceResult waitingAuth(
            String stepCode,
            String remoteHandle,
            String checkpoint,
            Duration retryAfter,
            String sanitizedCode
    ) {
        return waiting(
                TaskState.WAITING_AUTH,
                stepCode,
                remoteHandle,
                checkpoint,
                retryAfter,
                sanitizedCode,
                null,
                null
        );
    }

    public static AdvanceResult succeeded() {
        return new AdvanceResult(
                TaskState.SUCCEEDED, null, null, null, null, null, null, null
        );
    }

    public static AdvanceResult failed(String checkpoint, String sanitizedCode) {
        return failed(null, null, checkpoint, sanitizedCode);
    }

    public static AdvanceResult failed(
            String stepCode,
            String remoteHandle,
            String checkpoint,
            String sanitizedCode
    ) {
        return new AdvanceResult(
                TaskState.FAILED,
                stepCode,
                remoteHandle,
                checkpoint,
                null,
                SanitizedCode.require(sanitizedCode),
                null,
                null
        );
    }

    private static AdvanceResult waiting(
            TaskState state,
            String stepCode,
            String remoteHandle,
            String checkpoint,
            Duration retryAfter,
            String sanitizedCode,
            RiskShareLevel backoffShareLevel,
            String backoffProviderChannel
    ) {
        if (retryAfter == null || retryAfter.isNegative()) {
            throw new IllegalArgumentException("a waiting result requires a non-negative retryAfter");
        }
        return new AdvanceResult(
                state,
                stepCode,
                remoteHandle,
                checkpoint,
                retryAfter,
                SanitizedCode.require(sanitizedCode),
                backoffShareLevel,
                backoffProviderChannel
        );
    }

    public TaskState getNextState() {
        return nextState;
    }

    public String getStepCode() {
        return stepCode;
    }

    public String getRemoteHandle() {
        return remoteHandle;
    }

    public String getCheckpoint() {
        return checkpoint;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    public String getSanitizedCode() {
        return sanitizedCode;
    }

    public RiskShareLevel getBackoffShareLevel() {
        return backoffShareLevel;
    }

    public String getBackoffProviderChannel() {
        return backoffProviderChannel;
    }
}
