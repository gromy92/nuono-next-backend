package com.nuono.next.competitoranalysis;

import org.springframework.util.StringUtils;

final class CompetitorRefreshNaturalKey {
    private static final String PREFIX = "watchProduct:";

    private CompetitorRefreshNaturalKey() {
    }

    static String product(
            Long watchProductId,
            CompetitorRefreshExecutionMode executionMode,
            String batchKey
    ) {
        CompetitorRefreshExecutionMode mode = executionMode == null
                ? CompetitorRefreshExecutionMode.FULL_MANUAL
                : executionMode;
        String key = PREFIX + watchProductId;
        if (mode == CompetitorRefreshExecutionMode.FULL_MANUAL) {
            return key;
        }
        key += ":" + mode.taskKey();
        return mode == CompetitorRefreshExecutionMode.SCHEDULED_DETAIL
                && StringUtils.hasText(batchKey)
                ? key + ":" + batchKey
                : key;
    }
}
