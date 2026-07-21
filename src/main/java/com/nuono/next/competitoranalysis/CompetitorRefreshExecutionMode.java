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

    static CompetitorRefreshExecutionMode fromTriggerMode(String triggerMode) {
        for (CompetitorRefreshExecutionMode mode : values()) {
            if (mode.triggerMode.equals(triggerMode)) {
                return mode;
            }
        }
        return FULL_MANUAL;
    }
}
