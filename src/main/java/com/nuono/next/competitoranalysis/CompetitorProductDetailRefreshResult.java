package com.nuono.next.competitoranalysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public class CompetitorProductDetailRefreshResult {
    private int attemptedCount;
    private int requestAttemptCount;
    private int succeededCount;
    private int failedCount;
    private String firstErrorCode;
    private String firstErrorMessage;
    private String riskErrorCode;
    private String riskErrorMessage;
    private final List<CompetitorProductDetailTarget> succeededTargets = new ArrayList<>();
    private final List<CompetitorProductDetailFailure> failures = new ArrayList<>();
    private final List<CompetitorProductDetailFailure> deferredFailures = new ArrayList<>();

    public static CompetitorProductDetailRefreshResult empty() {
        return new CompetitorProductDetailRefreshResult();
    }

    static CompetitorProductDetailRefreshResult unavailable(String errorCode, String errorMessage) {
        CompetitorProductDetailRefreshResult result = new CompetitorProductDetailRefreshResult();
        result.attemptedCount = 1;
        result.recordFailure(errorCode, errorMessage);
        return result;
    }

    void recordAttempt() {
        attemptedCount++;
        requestAttemptCount++;
    }

    void recordTarget() {
        attemptedCount++;
    }

    void recordRequestAttempt() {
        requestAttemptCount++;
    }

    void recordAttempt(CompetitorProductDetailTarget target) {
        recordAttempt();
    }

    void recordSuccess() {
        succeededCount++;
    }

    void recordSuccess(CompetitorProductDetailTarget target) {
        recordSuccess();
        addTarget(succeededTargets, target);
    }

    void recordFailure(String errorCode, String errorMessage) {
        recordFailure(null, errorCode, errorMessage);
    }

    void recordFailure(
            CompetitorProductDetailTarget target,
            String errorCode,
            String errorMessage
    ) {
        failedCount++;
        failures.add(CompetitorProductDetailFailure.failed(target, errorCode, errorMessage));
        if (!StringUtils.hasText(firstErrorCode)) {
            firstErrorCode = errorCode;
        }
        if (!StringUtils.hasText(firstErrorMessage)) {
            firstErrorMessage = errorMessage;
        }
        if (!StringUtils.hasText(riskErrorCode) && isRiskBackoffFailure(errorCode)) {
            riskErrorCode = errorCode;
            riskErrorMessage = errorMessage;
        }
    }

    void recordDeferred(
            CompetitorProductDetailTarget target,
            String errorCode,
            String errorMessage
    ) {
        if (target == null || containsTarget(getDeferredTargets(), target)) {
            return;
        }
        deferredFailures.add(CompetitorProductDetailFailure.deferred(target, errorCode, errorMessage));
    }

    void addPriorCounts(int targetTotal, int priorSucceeded, int priorRequestAttempts) {
        attemptedCount = Math.max(attemptedCount, Math.max(0, targetTotal));
        requestAttemptCount += Math.max(0, priorRequestAttempts);
        succeededCount += Math.max(0, priorSucceeded);
    }

    void addPriorTerminalFailures(int count, String errorCode, String errorMessage) {
        int safeCount = Math.max(0, count);
        failedCount += safeCount;
        if (safeCount > 0 && !StringUtils.hasText(firstErrorCode)) {
            firstErrorCode = errorCode;
        }
        if (safeCount > 0 && !StringUtils.hasText(firstErrorMessage)) {
            firstErrorMessage = errorMessage;
        }
    }

    void useCumulativeCounts(
            int targetTotal,
            int requestAttempts,
            int succeeded,
            int terminalFailed,
            String terminalErrorCode,
            String terminalErrorMessage
    ) {
        attemptedCount = Math.max(0, targetTotal);
        requestAttemptCount = Math.max(0, requestAttempts);
        succeededCount = Math.max(0, succeeded);
        failedCount = Math.max(failedCount, Math.max(0, terminalFailed));
        if (failedCount > 0 && !StringUtils.hasText(firstErrorCode)) {
            firstErrorCode = terminalErrorCode;
        }
        if (failedCount > 0 && !StringUtils.hasText(firstErrorMessage)) {
            firstErrorMessage = terminalErrorMessage;
        }
    }

    public int getAttemptedCount() {
        return attemptedCount;
    }

    public int getRequestAttemptCount() {
        return requestAttemptCount;
    }

    public int getSucceededCount() {
        return succeededCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public String getFirstErrorCode() {
        return firstErrorCode;
    }

    public String getFirstErrorMessage() {
        return firstErrorMessage;
    }

    public boolean hasRiskBackoffFailure() {
        return StringUtils.hasText(riskErrorCode);
    }

    public String getRiskErrorCode() {
        return riskErrorCode;
    }

    public String getRiskErrorMessage() {
        return riskErrorMessage;
    }

    public int getDeferredCount() {
        return deferredFailures.size();
    }

    public List<CompetitorProductDetailTarget> getSucceededTargets() {
        return immutableTargets(succeededTargets);
    }

    public List<CompetitorProductDetailFailure> getFailures() {
        return Collections.unmodifiableList(new ArrayList<>(failures));
    }

    public List<CompetitorProductDetailFailure> getDeferredFailures() {
        return Collections.unmodifiableList(new ArrayList<>(deferredFailures));
    }

    public List<CompetitorProductDetailTarget> getFailedTargets() {
        List<CompetitorProductDetailTarget> targets = new ArrayList<>();
        for (CompetitorProductDetailFailure failure : failures) {
            addTarget(targets, failure == null ? null : failure.getTarget());
        }
        return immutableTargets(targets);
    }

    public List<CompetitorProductDetailTarget> getDeferredTargets() {
        List<CompetitorProductDetailTarget> targets = new ArrayList<>();
        for (CompetitorProductDetailFailure failure : deferredFailures) {
            addTarget(targets, failure == null ? null : failure.getTarget());
        }
        return immutableTargets(targets);
    }

    public List<CompetitorProductDetailTarget> getRetryTargets() {
        Map<String, CompetitorProductDetailTarget> targets = new LinkedHashMap<>();
        appendTargets(targets, getFailedTargets());
        appendTargets(targets, getDeferredTargets());
        return Collections.unmodifiableList(new ArrayList<>(targets.values()));
    }

    private boolean isRiskBackoffFailure(String errorCode) {
        return "RATE_LIMITED".equalsIgnoreCase(errorCode)
                || "BLOCKED_BY_RISK_CONTROL".equalsIgnoreCase(errorCode)
                || "CAPTCHA_REQUIRED".equalsIgnoreCase(errorCode);
    }

    private void appendTargets(
            Map<String, CompetitorProductDetailTarget> targets,
            List<CompetitorProductDetailTarget> additions
    ) {
        for (CompetitorProductDetailTarget target : additions) {
            if (target != null) {
                targets.putIfAbsent(target.identityKey(), target);
            }
        }
    }

    private void addTarget(
            List<CompetitorProductDetailTarget> targets,
            CompetitorProductDetailTarget target
    ) {
        if (target != null && !containsTarget(targets, target)) {
            targets.add(target);
        }
    }

    private boolean containsTarget(
            List<CompetitorProductDetailTarget> targets,
            CompetitorProductDetailTarget target
    ) {
        return target != null && targets.contains(target);
    }

    private List<CompetitorProductDetailTarget> immutableTargets(
            List<CompetitorProductDetailTarget> targets
    ) {
        return Collections.unmodifiableList(new ArrayList<>(targets));
    }
}
