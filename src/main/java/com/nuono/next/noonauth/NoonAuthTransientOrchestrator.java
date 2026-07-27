package com.nuono.next.noonauth;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryAttemptResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryFailureStage;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectResult;
import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import com.nuono.next.noonauth.gateway.NoonAuthTransientFailure;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class NoonAuthTransientOrchestrator {
    private final NoonAuthTransientBackoffGuard guard;

    NoonAuthTransientOrchestrator(NoonAuthTransientBackoffGuard guard) {
        this.guard = guard;
    }

    Selection selectDueTargets(List<NoonAuthRecoveryProjectTarget> targets) {
        List<NoonAuthRecoveryProjectTarget> dueTargets = new ArrayList<>();
        List<NoonAuthRecoveryProjectTarget> mappedTargets = new ArrayList<>();
        List<NoonAuthRecoveryProjectTarget> unmappedTargets = new ArrayList<>();
        Map<String, Long> logicalStoreIds = new LinkedHashMap<>();
        LocalDateTime nextBlockedUntil = null;
        for (NoonAuthRecoveryProjectTarget target : targets) {
            Long logicalStoreId = guard.resolveLogicalStoreId(target);
            if (guard.isEnabled() && logicalStoreId == null) {
                unmappedTargets.add(target);
                continue;
            }
            mappedTargets.add(target);
            if (logicalStoreId != null) {
                logicalStoreIds.put(target.key(), logicalStoreId);
            }
            Optional<NoonAuthTransientBackoffState> hold = guard.currentHold(logicalStoreId);
            if (hold.isEmpty()) {
                dueTargets.add(target);
            } else {
                nextBlockedUntil = earlier(nextBlockedUntil, hold.get().getBlockedUntil());
            }
        }
        return new Selection(
                List.copyOf(dueTargets),
                List.copyOf(mappedTargets),
                List.copyOf(unmappedTargets),
                Map.copyOf(logicalStoreIds),
                nextBlockedUntil
        );
    }

    IdentityFailureOutcome recordIdentityFailure(
            List<NoonAuthRecoveryProjectTarget> targets,
            Map<String, Long> logicalStoreIds,
            LocalDateTime existingNextBlockedUntil,
            LocalDateTime fallbackBlockedUntil,
            NoonAuthRecoveryAttemptResult result,
            FenceSupplier fenceSupplier
    ) {
        LocalDateTime nextBlockedUntil = existingNextBlockedUntil;
        for (NoonAuthRecoveryProjectTarget target : targets) {
            for (NoonAuthTransientFailure failure : result.getTransientFailures()) {
                NoonAuthTransientBackoffWriteFence fence = fenceSupplier.acquire();
                if (fence == null || recordFailure(
                        target,
                        logicalStoreIds.get(target.key()),
                        fence,
                        failure.getStage(),
                        failure.getErrorType(),
                        safeDiagnostic(failure.getSafeDiagnostic())
                ) == null) {
                    return IdentityFailureOutcome.fenceLost();
                }
            }
            Optional<NoonAuthTransientBackoffState> storeHold =
                    guard.currentHold(logicalStoreIds.get(target.key()));
            if (storeHold.isPresent()) {
                nextBlockedUntil = earlier(nextBlockedUntil, storeHold.get().getBlockedUntil());
            }
        }
        String failureCode = result.getTransientFailures().size() == 1
                ? result.getTransientErrorType().name()
                : "PROJECT_TRANSIENT_BACKOFF";
        return IdentityFailureOutcome.recorded(
                failureCode,
                safeDiagnostic(result.getSafeDiagnostic()),
                nextBlockedUntil == null ? fallbackBlockedUntil : nextBlockedUntil
        );
    }

    NoonAuthTransientBackoffState recordFailure(
            NoonAuthRecoveryProjectTarget target,
            Long logicalStoreId,
            NoonAuthTransientBackoffWriteFence fence,
            NoonAuthRecoveryFailureStage stage,
            NoonTransientErrorType errorType,
            String diagnostic
    ) {
        return guard.recordFailure(
                target,
                logicalStoreId,
                fence,
                stage,
                errorType,
                safeDiagnostic(diagnostic)
        );
    }

    boolean recordSuccess(Long logicalStoreId, NoonAuthTransientBackoffWriteFence fence) {
        return guard.recordSuccess(logicalStoreId, fence);
    }

    Long resolveLogicalStoreId(NoonAuthRecoveryProjectTarget target) {
        return guard.resolveLogicalStoreId(target);
    }

    boolean hasFailureForRecovery(
            List<NoonAuthRecoveryProjectTarget> targets,
            Map<String, Long> logicalStoreIds,
            Long recoveryId
    ) {
        for (NoonAuthRecoveryProjectTarget target : targets) {
            if (guard.hasFailureForRecovery(logicalStoreIds.get(target.key()), recoveryId)) {
                return true;
            }
        }
        return false;
    }

    String onlyTransientFailureCode(
            Map<String, NoonAuthRecoveryProjectResult> resultsByKey,
            List<NoonAuthRecoveryProjectTarget> targets
    ) {
        for (NoonAuthRecoveryProjectTarget target : targets) {
            NoonAuthRecoveryProjectResult result = resultsByKey.get(target.key());
            if (result != null && result.isTransientFailure()) {
                return result.getTransientErrorType().name();
            }
        }
        return "PROJECT_TRANSIENT_BACKOFF";
    }

    static LocalDateTime earlier(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return second.isBefore(first) ? second : first;
    }

    private static String safeDiagnostic(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    @FunctionalInterface
    interface FenceSupplier {
        NoonAuthTransientBackoffWriteFence acquire();
    }

    static final class Selection {
        final List<NoonAuthRecoveryProjectTarget> dueTargets;
        final List<NoonAuthRecoveryProjectTarget> mappedTargets;
        final List<NoonAuthRecoveryProjectTarget> unmappedTargets;
        final Map<String, Long> logicalStoreIds;
        final LocalDateTime nextBlockedUntil;

        private Selection(
                List<NoonAuthRecoveryProjectTarget> dueTargets,
                List<NoonAuthRecoveryProjectTarget> mappedTargets,
                List<NoonAuthRecoveryProjectTarget> unmappedTargets,
                Map<String, Long> logicalStoreIds,
                LocalDateTime nextBlockedUntil
        ) {
            this.dueTargets = dueTargets;
            this.mappedTargets = mappedTargets;
            this.unmappedTargets = unmappedTargets;
            this.logicalStoreIds = logicalStoreIds;
            this.nextBlockedUntil = nextBlockedUntil;
        }
    }

    static final class IdentityFailureOutcome {
        final boolean recorded;
        final String failureCode;
        final String diagnostic;
        final LocalDateTime nextBlockedUntil;

        private IdentityFailureOutcome(
                boolean recorded,
                String failureCode,
                String diagnostic,
                LocalDateTime nextBlockedUntil
        ) {
            this.recorded = recorded;
            this.failureCode = failureCode;
            this.diagnostic = diagnostic;
            this.nextBlockedUntil = nextBlockedUntil;
        }

        private static IdentityFailureOutcome fenceLost() {
            return new IdentityFailureOutcome(false, null, null, null);
        }

        private static IdentityFailureOutcome recorded(
                String failureCode,
                String diagnostic,
                LocalDateTime nextBlockedUntil
        ) {
            return new IdentityFailureOutcome(
                    true,
                    failureCode,
                    diagnostic,
                    nextBlockedUntil
            );
        }
    }
}
