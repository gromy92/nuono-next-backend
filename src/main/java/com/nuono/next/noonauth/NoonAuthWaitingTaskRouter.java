package com.nuono.next.noonauth;

import com.nuono.next.noonpull.NoonPullDataDomain;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

final class NoonAuthWaitingTaskRouter {
    private final NoonAuthRecoveryRepository repository;
    private final List<NoonAuthWaitingTaskHandler> handlers;

    NoonAuthWaitingTaskRouter(
            NoonAuthRecoveryRepository repository,
            List<NoonAuthWaitingTaskHandler> handlers
    ) {
        this.repository = repository;
        this.handlers = handlers == null ? Collections.emptyList() : handlers;
    }

    NoonAuthWaitingTaskOutcome resume(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            LocalDateTime now
    ) {
        NoonAuthWaitingTaskHandler handler = handler(item.getSourceDomain());
        if (handler != null) {
            return handler.resume(item, recoveryStatus, recoveryVersion, leaseToken, now);
        }
        if (!isPullDomain(item.getSourceDomain())) {
            return NoonAuthWaitingTaskOutcome.STALE;
        }
        return repository.requeueBlockedTaskAfterRecoveryCas(
                item.getSourceTaskId(),
                item.getRecoveryId(),
                recoveryStatus,
                recoveryVersion,
                leaseToken,
                now
        ) ? NoonAuthWaitingTaskOutcome.RESUMED : NoonAuthWaitingTaskOutcome.STALE;
    }

    NoonAuthWaitingTaskOutcome fail(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            String failureCode,
            String diagnostic,
            LocalDateTime now
    ) {
        NoonAuthWaitingTaskHandler handler = handler(item.getSourceDomain());
        if (handler != null) {
            return handler.fail(
                    item,
                    recoveryStatus,
                    recoveryVersion,
                    leaseToken,
                    failureCode,
                    diagnostic,
                    now
            );
        }
        if (!isPullDomain(item.getSourceDomain())) {
            return NoonAuthWaitingTaskOutcome.STALE;
        }
        return repository.failBlockedTaskAfterRecovery(
                item.getSourceTaskId(),
                item.getRecoveryId(),
                recoveryStatus,
                recoveryVersion,
                leaseToken,
                failureCode,
                diagnostic,
                now
        ) ? NoonAuthWaitingTaskOutcome.MANUAL_REVIEW : NoonAuthWaitingTaskOutcome.STALE;
    }

    NoonAuthWaitingTaskOutcome hold(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            String failureCode,
            String diagnostic,
            LocalDateTime now
    ) {
        NoonAuthWaitingTaskHandler handler = handler(item.getSourceDomain());
        return handler == null
                ? NoonAuthWaitingTaskOutcome.MANUAL_REVIEW
                : handler.hold(
                        item, recoveryStatus, recoveryVersion, leaseToken,
                        failureCode, diagnostic, now
                );
    }

    private NoonAuthWaitingTaskHandler handler(String sourceDomain) {
        for (NoonAuthWaitingTaskHandler handler : handlers) {
            if (handler != null && handler.supports(sourceDomain)) {
                return handler;
            }
        }
        return null;
    }

    private boolean isPullDomain(String sourceDomain) {
        if ("NOON_PULL".equalsIgnoreCase(sourceDomain)) {
            return true;
        }
        if (sourceDomain == null) {
            // Before migration 238 only noon_pull_task could populate source_task_id.
            return true;
        }
        try {
            NoonPullDataDomain.valueOf(sourceDomain.trim().toUpperCase(java.util.Locale.ROOT));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
