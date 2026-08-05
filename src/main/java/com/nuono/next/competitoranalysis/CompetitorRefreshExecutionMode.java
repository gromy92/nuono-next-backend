package com.nuono.next.competitoranalysis;

enum CompetitorRefreshExecutionMode {
    FULL_MANUAL("MANUAL_REFRESH", "full", true, true),
    FULL_MANUAL_MONITOR("MANUAL_MONITOR", "full-monitor", true, true),
    SCHEDULED_RANK("SCHEDULED_RANK_MONITOR", "rank", true, false),
    SCHEDULED_DETAIL("SCHEDULED_DETAIL_MONITOR", "detail", false, true);

    private final String triggerMode;
    private final String taskKey;
    private final boolean runsRank;
    private final boolean runsDetail;

    CompetitorRefreshExecutionMode(String triggerMode, String taskKey, boolean runsRank, boolean runsDetail) {
        this.triggerMode = triggerMode;
        this.taskKey = taskKey;
        this.runsRank = runsRank;
        this.runsDetail = runsDetail;
    }

    String triggerMode() {
        return triggerMode;
    }

    String taskKey() {
        return taskKey;
    }

    boolean runsRank() {
        return runsRank;
    }

    boolean runsDetail() {
        return runsDetail;
    }

    boolean isManual() {
        return this == FULL_MANUAL || this == FULL_MANUAL_MONITOR;
    }

    static CompetitorRefreshExecutionMode requireManual(
            CompetitorRefreshExecutionMode mode
    ) {
        if (mode == null || !mode.isManual()) {
            throw new CompetitorRefreshRecoveryIdentityException(
                    "Legacy scheduled competitor execution is retired."
            );
        }
        return mode;
    }

    static CompetitorRefreshExecutionMode requireKnown(CompetitorRefreshExecutionMode mode) {
        if (mode == null) {
            throw new CompetitorRefreshRecoveryIdentityException(
                    "Competitor refresh execution mode is required."
            );
        }
        return mode;
    }

    static CompetitorRefreshExecutionMode requireManualMonitor(
            CompetitorRefreshExecutionMode mode
    ) {
        if (mode != FULL_MANUAL_MONITOR) {
            throw new CompetitorRefreshRecoveryIdentityException(
                    "Only manual competitor monitoring may use the batch executor."
            );
        }
        return mode;
    }

    static CompetitorRefreshExecutionMode strictFromTriggerMode(String triggerMode) {
        for (CompetitorRefreshExecutionMode mode : values()) {
            if (mode.triggerMode.equals(triggerMode)) {
                return mode;
            }
        }
        throw new CompetitorRefreshRecoveryIdentityException(
                "Unknown competitor refresh trigger mode: " + triggerMode
        );
    }
}
