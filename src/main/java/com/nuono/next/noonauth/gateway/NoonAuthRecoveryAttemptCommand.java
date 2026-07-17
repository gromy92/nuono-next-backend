package com.nuono.next.noonauth.gateway;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class NoonAuthRecoveryAttemptCommand {
    private final long recoveryId;
    private final int generation;
    private final Instant requestedAt;
    private final Set<String> excludedMessageKeyHashes;
    private final List<NoonAuthRecoveryProjectTarget> projectTargets;
    private final LeaseHeartbeat leaseHeartbeat;
    private final BeforeOtpSend beforeOtpSend;
    private boolean beforeOtpSendInvoked;

    public NoonAuthRecoveryAttemptCommand(
            long recoveryId,
            int generation,
            Instant requestedAt,
            Set<String> excludedMessageKeyHashes,
            List<NoonAuthRecoveryProjectTarget> projectTargets,
            LeaseHeartbeat leaseHeartbeat,
            BeforeOtpSend beforeOtpSend
    ) {
        this.recoveryId = recoveryId;
        this.generation = Math.max(1, generation);
        this.requestedAt = requestedAt;
        this.excludedMessageKeyHashes = excludedMessageKeyHashes == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(excludedMessageKeyHashes));
        this.projectTargets = projectTargets == null
                ? Collections.emptyList()
                : List.copyOf(projectTargets);
        this.leaseHeartbeat = Objects.requireNonNull(leaseHeartbeat, "leaseHeartbeat must not be null");
        this.beforeOtpSend = Objects.requireNonNull(beforeOtpSend, "beforeOtpSend must not be null");
    }

    public long getRecoveryId() {
        return recoveryId;
    }

    public int getGeneration() {
        return generation;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Set<String> getExcludedMessageKeyHashes() {
        return excludedMessageKeyHashes;
    }

    public List<NoonAuthRecoveryProjectTarget> getProjectTargets() {
        return projectTargets;
    }

    public void heartbeatOrThrow() {
        try {
            if (!leaseHeartbeat.renew()) {
                throw new LeaseLostException();
            }
        } catch (LeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LeaseLostException(exception);
        }
    }

    public synchronized void beforeOtpSendOrThrow() {
        if (beforeOtpSendInvoked) {
            throw new LeaseLostException();
        }
        beforeOtpSendInvoked = true;
        try {
            if (!beforeOtpSend.reserve()) {
                throw new LeaseLostException();
            }
        } catch (LeaseLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LeaseLostException(exception);
        }
    }

    @FunctionalInterface
    public interface LeaseHeartbeat {
        boolean renew();
    }

    @FunctionalInterface
    public interface BeforeOtpSend {
        boolean reserve();
    }

    public static final class LeaseLostException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private LeaseLostException() {
            super("auth recovery lease lost");
        }

        private LeaseLostException(Throwable cause) {
            super("auth recovery lease lost", cause);
        }
    }
}
