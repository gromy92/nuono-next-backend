package com.nuono.next.productselection;

public class Ali1688OfferDetailCompletionResult {

    private final String outcome;
    private final String message;
    private final int attemptCount;
    private final int enrichedCount;
    private final int failedCount;

    public Ali1688OfferDetailCompletionResult(
            String outcome,
            String message,
            int attemptCount,
            int enrichedCount,
            int failedCount
    ) {
        this.outcome = outcome;
        this.message = message;
        this.attemptCount = Math.max(0, attemptCount);
        this.enrichedCount = Math.max(0, enrichedCount);
        this.failedCount = Math.max(0, failedCount);
    }

    public static Ali1688OfferDetailCompletionResult notAttempted(String message) {
        return new Ali1688OfferDetailCompletionResult("not_attempted", message, 0, 0, 0);
    }

    public static Ali1688OfferDetailCompletionResult completed(int attemptCount, int enrichedCount, String message) {
        return new Ali1688OfferDetailCompletionResult("completed", message, attemptCount, enrichedCount, 0);
    }

    public static Ali1688OfferDetailCompletionResult partialEnriched(
            int attemptCount,
            int enrichedCount,
            int failedCount,
            String message
    ) {
        return new Ali1688OfferDetailCompletionResult("partial_enriched", message, attemptCount, enrichedCount, failedCount);
    }

    public static Ali1688OfferDetailCompletionResult failed(int attemptCount, int failedCount, String message) {
        return new Ali1688OfferDetailCompletionResult("failed", message, attemptCount, 0, failedCount);
    }

    public static Ali1688OfferDetailCompletionResult blockedByCaptcha(int attemptCount, int failedCount, String message) {
        return new Ali1688OfferDetailCompletionResult("blocked_by_captcha", message, attemptCount, 0, failedCount);
    }

    public String getOutcome() {
        return outcome;
    }

    public String getMessage() {
        return message;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getEnrichedCount() {
        return enrichedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public boolean isAttempted() {
        return attemptCount > 0;
    }
}
