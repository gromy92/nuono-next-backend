package com.nuono.next.product;

public final class ProductDetailBaselineEnqueueResult {
    private final boolean enabled;
    private final int candidateCount;
    private final int enqueuedCount;
    private final int failedCount;
    private final int staleRecoveredCount;

    private ProductDetailBaselineEnqueueResult(
            boolean enabled,
            int candidateCount,
            int enqueuedCount,
            int failedCount,
            int staleRecoveredCount
    ) {
        this.enabled = enabled;
        this.candidateCount = candidateCount;
        this.enqueuedCount = enqueuedCount;
        this.failedCount = failedCount;
        this.staleRecoveredCount = staleRecoveredCount;
    }

    static ProductDetailBaselineEnqueueResult disabled() {
        return new ProductDetailBaselineEnqueueResult(false, 0, 0, 0, 0);
    }

    static ProductDetailBaselineEnqueueResult enabled(int candidates, int enqueued, int failed, int recovered) {
        return new ProductDetailBaselineEnqueueResult(true, candidates, enqueued, failed, recovered);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public int getEnqueuedCount() {
        return enqueuedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getStaleRecoveredCount() {
        return staleRecoveredCount;
    }
}
