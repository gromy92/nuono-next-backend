package com.nuono.next.datapull.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Objects;

/**
 * Computes non-blocking provider retry delays behind one small Interface.
 *
 * <p>Attempts are one-based. A valid Retry-After value wins unchanged;
 * otherwise the policy applies capped exponential delay and deterministic,
 * key-scoped positive jitter. The final computed fallback never exceeds the
 * configured ceiling.</p>
 */
public final class BackoffPolicy {

    private final Duration baseDelay;
    private final Duration maximumDelay;
    private final double jitterRatio;

    public BackoffPolicy(Duration baseDelay, Duration maximumDelay, double jitterRatio) {
        this.baseDelay = requirePositive(baseDelay, "baseDelay");
        this.maximumDelay = requirePositive(maximumDelay, "maximumDelay");
        if (this.baseDelay.compareTo(this.maximumDelay) > 0) {
            throw new IllegalArgumentException("baseDelay must not exceed maximumDelay");
        }
        if (!Double.isFinite(jitterRatio) || jitterRatio < 0.0d || jitterRatio > 1.0d) {
            throw new IllegalArgumentException("jitterRatio must be between 0 and 1");
        }
        this.jitterRatio = jitterRatio;
    }

    public Duration delayFor(
            ProviderOutcome<?> outcome,
            BackoffKey key,
            int consecutiveAttempt
    ) {
        ProviderOutcome<?> nonNullOutcome = Objects.requireNonNull(outcome, "outcome");
        BackoffKey nonNullKey = Objects.requireNonNull(key, "key");
        if (consecutiveAttempt < 1) {
            throw new IllegalArgumentException("consecutiveAttempt must be at least 1");
        }
        if (nonNullOutcome.getType() != ProviderOutcomeType.CONTRACT_ERROR
                && nonNullOutcome.getType() != ProviderOutcomeType.RISK_CONTROL
                && nonNullOutcome.getType() != ProviderOutcomeType.TRANSIENT) {
            throw new IllegalArgumentException("backoff is only valid for retryable provider outcomes");
        }
        if (nonNullOutcome.getRetryAfter() != null) {
            return nonNullOutcome.getRetryAfter();
        }

        long baseMillis = baseDelay.toMillis();
        long maximumMillis = maximumDelay.toMillis();
        long exponentialMillis = cappedExponential(baseMillis, maximumMillis, consecutiveAttempt - 1);
        long availableMillis = maximumMillis - exponentialMillis;
        long ratioMillis = (long) Math.floor(exponentialMillis * jitterRatio);
        long jitterCeiling = Math.min(availableMillis, ratioMillis);
        long jitterMillis = deterministicJitter(nonNullKey, consecutiveAttempt, jitterCeiling);
        return Duration.ofMillis(exponentialMillis + jitterMillis);
    }

    public RiskShareLevel shareLevelFor(ProviderOutcome<?> outcome, BackoffKey key) {
        ProviderOutcome<?> nonNull = Objects.requireNonNull(outcome, "outcome");
        BackoffKey nonNullKey = Objects.requireNonNull(key, "key");
        if (nonNull.getType() == ProviderOutcomeType.RISK_CONTROL) {
            if (nonNull.getShareLevel() == RiskShareLevel.EXIT) {
                nonNullKey.requireEgressKey();
            }
            return nonNull.getShareLevel();
        }
        return RiskShareLevel.EXACT;
    }

    private static Duration requirePositive(Duration duration, String name) {
        Duration nonNull = Objects.requireNonNull(duration, name);
        if (nonNull.isZero() || nonNull.isNegative() || nonNull.toMillis() == 0L) {
            throw new IllegalArgumentException(name + " must be at least one millisecond");
        }
        return nonNull;
    }

    private static long cappedExponential(long base, long maximum, int doublings) {
        long value = base;
        for (int index = 0; index < doublings && value < maximum; index++) {
            if (value > maximum / 2L) {
                return maximum;
            }
            value *= 2L;
        }
        return Math.min(value, maximum);
    }

    private static long deterministicJitter(BackoffKey key, int attempt, long ceiling) {
        if (ceiling <= 0L) {
            return 0L;
        }
        byte[] digest = sha256((key.stableIdentity() + "#" + attempt).getBytes(StandardCharsets.UTF_8));
        long sample = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            sample = (sample << 8) | (digest[index] & 0xffL);
        }
        double unit = (sample & Long.MAX_VALUE) / (double) Long.MAX_VALUE;
        return (long) Math.floor(unit * ceiling);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 must be available in every Java runtime", error);
        }
    }

    public Duration getBaseDelay() {
        return baseDelay;
    }

    public Duration getMaximumDelay() {
        return maximumDelay;
    }

    public double getJitterRatio() {
        return jitterRatio;
    }

    public RiskShareLevel getDefaultShareLevel() {
        return RiskShareLevel.EXACT;
    }
}
