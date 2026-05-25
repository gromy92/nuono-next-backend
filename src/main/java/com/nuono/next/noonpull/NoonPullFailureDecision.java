package com.nuono.next.noonpull;

import java.time.LocalDateTime;

public class NoonPullFailureDecision {
    private final NoonPullFailureType failureType;
    private final NoonPullRetryAction action;
    private final boolean retryable;
    private final boolean requiresManualAction;
    private final boolean pausePlan;
    private final LocalDateTime nextRetryAt;
    private final String summary;

    public NoonPullFailureDecision(
            NoonPullFailureType failureType,
            NoonPullRetryAction action,
            boolean retryable,
            boolean requiresManualAction,
            boolean pausePlan,
            LocalDateTime nextRetryAt,
            String summary
    ) {
        this.failureType = failureType;
        this.action = action;
        this.retryable = retryable;
        this.requiresManualAction = requiresManualAction;
        this.pausePlan = pausePlan;
        this.nextRetryAt = nextRetryAt;
        this.summary = summary;
    }

    public NoonPullFailureType getFailureType() {
        return failureType;
    }

    public NoonPullRetryAction getAction() {
        return action;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean requiresManualAction() {
        return requiresManualAction;
    }

    public boolean shouldPausePlan() {
        return pausePlan;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public String getSummary() {
        return summary;
    }
}
