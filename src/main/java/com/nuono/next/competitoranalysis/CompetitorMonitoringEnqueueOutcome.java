package com.nuono.next.competitoranalysis;

enum CompetitorMonitoringEnqueueOutcome {
    CREATED,
    REUSED_SAME_BATCH,
    DEFERRED_ACTIVE,
    STALE_TERMINAL_RECONCILED
}
