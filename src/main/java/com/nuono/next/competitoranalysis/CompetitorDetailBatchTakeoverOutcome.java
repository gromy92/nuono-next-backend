package com.nuono.next.competitoranalysis;

final class CompetitorDetailBatchTakeoverOutcome {
    private final int olderSuperseded;
    private final boolean currentSuperseded;

    private CompetitorDetailBatchTakeoverOutcome(
            int olderSuperseded,
            boolean currentSuperseded
    ) {
        this.olderSuperseded = olderSuperseded;
        this.currentSuperseded = currentSuperseded;
    }

    static CompetitorDetailBatchTakeoverOutcome olderSuperseded(int count) {
        return new CompetitorDetailBatchTakeoverOutcome(count, false);
    }

    static CompetitorDetailBatchTakeoverOutcome currentSuperseded() {
        return new CompetitorDetailBatchTakeoverOutcome(0, true);
    }

    int getOlderSuperseded() {
        return olderSuperseded;
    }

    boolean isCurrentSuperseded() {
        return currentSuperseded;
    }
}
