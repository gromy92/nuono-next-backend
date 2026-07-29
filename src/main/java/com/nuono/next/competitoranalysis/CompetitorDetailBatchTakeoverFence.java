package com.nuono.next.competitoranalysis;

final class CompetitorDetailBatchTakeoverFence implements Runnable {
    private final CompetitorDetailBatchTakeover takeover;
    private final Long taskId;
    private final Long runId;
    private final Long watchProductId;

    CompetitorDetailBatchTakeoverFence(
            CompetitorDetailBatchTakeover takeover,
            Long taskId,
            Long runId,
            Long watchProductId
    ) {
        this.takeover = takeover;
        this.taskId = taskId;
        this.runId = runId;
        this.watchProductId = watchProductId;
    }

    @Override
    public void run() {
        CompetitorDetailBatchTakeoverOutcome outcome =
                takeover.takeoverOlderBatches(taskId, runId, watchProductId);
        if (outcome.isCurrentSuperseded()) {
            throw new CompetitorRefreshLeaseLostException(taskId, runId);
        }
    }
}
