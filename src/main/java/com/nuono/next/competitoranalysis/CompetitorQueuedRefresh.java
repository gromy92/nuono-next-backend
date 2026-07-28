package com.nuono.next.competitoranalysis;

final class CompetitorQueuedRefresh {
    private final CompetitorRefreshRunView view;
    private final CompetitorMonitoringEnqueueOutcome outcome;

    CompetitorQueuedRefresh(
            CompetitorRefreshRunView view,
            CompetitorMonitoringEnqueueOutcome outcome
    ) {
        this.view = view;
        this.outcome = outcome;
    }

    CompetitorRefreshRunView getView() {
        return view;
    }

    CompetitorMonitoringEnqueueOutcome getOutcome() {
        return outcome;
    }
}
