package com.nuono.next.noonauth;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonAuthTransientBackoffGuard {
    private static final int INITIAL_BACKOFF_MINUTES = 2;

    private final NoonAuthTransientBackoffRepository repository;
    private final Clock clock;

    @Autowired
    public NoonAuthTransientBackoffGuard(
            ObjectProvider<NoonAuthTransientBackoffRepository> repositoryProvider
    ) {
        this(
                repositoryProvider == null ? null : repositoryProvider.getIfAvailable(),
                Clock.systemUTC()
        );
    }

    NoonAuthTransientBackoffGuard(
            NoonAuthTransientBackoffRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    static NoonAuthTransientBackoffGuard disabled(Clock clock) {
        return new NoonAuthTransientBackoffGuard(null, clock);
    }

    public boolean isEnabled() {
        return repository != null;
    }

    public Long resolveLogicalStoreId(NoonAuthRecoveryProjectTarget target) {
        if (repository == null || !validTarget(target)) {
            return null;
        }
        return repository.resolveLogicalStoreId(
                target.getOwnerUserId(),
                target.getProjectCode()
        );
    }

    public Optional<NoonAuthTransientBackoffState> currentHold(Long logicalStoreId) {
        if (repository == null || logicalStoreId == null) {
            return Optional.empty();
        }
        List<NoonAuthTransientBackoffState> holds =
                repository.listActiveHolds(logicalStoreId, now());
        if (holds == null || holds.isEmpty()) {
            return Optional.empty();
        }
        return holds.stream()
                .filter(state -> state != null && state.getBlockedUntil() != null)
                .max((left, right) -> left.getBlockedUntil().compareTo(right.getBlockedUntil()));
    }

    public NoonAuthTransientBackoffState recordFailure(
            NoonAuthRecoveryProjectTarget target,
            Long logicalStoreId,
            NoonAuthTransientBackoffWriteFence fence,
            NoonAuthRecoveryFailureStage stage,
            NoonTransientErrorType errorType,
            String diagnosticSummary
    ) {
        if (!validTarget(target) || stage == null || errorType == null) {
            throw new IllegalArgumentException("Complete project transient failure facts are required.");
        }
        if (repository != null && (logicalStoreId == null || fence == null)) {
            throw new IllegalArgumentException(
                    "Logical store and recovery fence are required for persisted transient backoff."
            );
        }
        LocalDateTime failedAt = now();
        NoonAuthTransientBackoffState failure = new NoonAuthTransientBackoffState();
        failure.setLogicalStoreId(logicalStoreId);
        failure.setErrorType(errorType);
        failure.setOwnerUserId(target.getOwnerUserId());
        failure.setProjectCode(target.getProjectCode());
        failure.setLastStoreCode(target.getStoreCode());
        failure.setSourceStage(stage);
        failure.setSourceRecoveryId(fence == null ? null : fence.getRecoveryId());
        failure.setAttemptCount(1);
        failure.setBlockedUntil(failedAt.plusMinutes(INITIAL_BACKOFF_MINUTES));
        failure.setLastFailedAt(failedAt);
        failure.setDiagnosticSummary(diagnosticSummary);
        failure.setCreatedAt(failedAt);
        failure.setUpdatedAt(failedAt);
        if (repository == null) {
            return failure;
        }
        NoonAuthTransientBackoffState persisted =
                repository.incrementFailure(failure, fence, failedAt);
        if (persisted != null && persisted.getBlockedUntil() == null) {
            throw new IllegalStateException("Persisted transient auth backoff has no deadline.");
        }
        return persisted;
    }

    public boolean recordSuccess(
            Long logicalStoreId,
            NoonAuthTransientBackoffWriteFence fence
    ) {
        if (repository == null) {
            return true;
        }
        if (logicalStoreId == null || fence == null) {
            return false;
        }
        return repository.resetForRecovery(
                logicalStoreId,
                fence.getRecoveryId(),
                fence,
                now()
        );
    }

    public boolean hasFailureForRecovery(
            Long logicalStoreId,
            Long recoveryId
    ) {
        if (repository == null || logicalStoreId == null || recoveryId == null) {
            return false;
        }
        return repository.hasFailureForRecovery(logicalStoreId, recoveryId);
    }

    private boolean validTarget(NoonAuthRecoveryProjectTarget target) {
        return target != null
                && target.getOwnerUserId() != null
                && StringUtils.hasText(target.getProjectCode());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
