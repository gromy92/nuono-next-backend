package com.nuono.next.competitoranalysis;

final class CompetitorRefreshLeaseLostException extends RuntimeException {
    CompetitorRefreshLeaseLostException(Long taskId, Long runId) {
        super("Competitor refresh execution lease was lost: taskId="
                + taskId + ", runId=" + runId);
    }
}
