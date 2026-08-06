package com.nuono.next.noonpull;

import org.springframework.util.StringUtils;

/** Preserves the legacy risk-control versus ordinary-failure transition rules. */
final class LegacyNoonPullFailureRecorder {
    private final NoonPullFoundationService foundationService;
    private final NoonRiskBackoffGuard riskBackoffGuard;
    private final NoonPullFailurePolicy failurePolicy;

    LegacyNoonPullFailureRecorder(
            NoonPullFoundationService foundationService,
            NoonRiskBackoffGuard riskBackoffGuard,
            NoonPullFailurePolicy failurePolicy
    ) {
        this.foundationService = foundationService;
        this.riskBackoffGuard = riskBackoffGuard == null
                ? NoonRiskBackoffGuard.disabled() : riskBackoffGuard;
        this.failurePolicy = failurePolicy == null
                ? new NoonPullFailurePolicy() : failurePolicy;
    }

    void interfaceFailure(
            NoonPullTaskRecord task,
            NoonInterfacePullRequest request,
            String rawFailure
    ) {
        NoonPullFailureType failureType = failurePolicy.classify(rawFailure);
        if (!isRiskBackoffFailure(failureType)) {
            foundationService.markFailedWithPolicy(task.getId(), rawFailure, 1);
            return;
        }
        NoonRiskBackoffHold hold = riskBackoffGuard.recordRiskSignal(
                NoonRiskBackoffScope.interfacePull(request),
                failureType.code(),
                task.getDataDomain().name(),
                task.getId(),
                null,
                rawFailure
        );
        foundationService.recordInterfaceRiskBackoffDelay(
                task.getId(), hold, request.getRequestName()
        );
    }

    void reportFailure(
            NoonPullTaskRecord task,
            NoonReportPullRequest request,
            String exportCode,
            int attempt,
            String rawFailure
    ) {
        NoonPullFailureType failureType = failurePolicy.classify(rawFailure);
        if (!isRiskBackoffFailure(failureType)) {
            if (StringUtils.hasText(exportCode)) {
                foundationService.recordReportExportTransientFailure(
                        task.getId(), exportCode, null, attempt, rawFailure
                );
            } else {
                foundationService.markFailedWithPolicy(task.getId(), rawFailure, attempt);
            }
            return;
        }
        NoonRiskBackoffHold hold = riskBackoffGuard.recordRiskSignal(
                NoonRiskBackoffScope.report(request),
                failureType.code(),
                task.getDataDomain().name(),
                task.getId(),
                null,
                rawFailure
        );
        foundationService.recordReportRiskBackoffDelay(
                task.getId(), hold, request.descriptor()
        );
    }

    String safeMessage(RuntimeException exception) {
        if (exception == null) {
            return "unknown failure";
        }
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private boolean isRiskBackoffFailure(NoonPullFailureType failureType) {
        return failureType == NoonPullFailureType.RATE_LIMITED
                || failureType == NoonPullFailureType.CAPTCHA_REQUIRED
                || failureType == NoonPullFailureType.BLOCKED_BY_RISK_CONTROL;
    }
}
