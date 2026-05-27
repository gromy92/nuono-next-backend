package com.nuono.next.sales;

import java.util.List;

public class SalesHistoryBackfillResult {
    private final int plannedTaskCount;
    private final List<Long> plannedTaskIds;
    private final List<Long> gapIds;
    private final List<String> categories;
    private final String message;

    public SalesHistoryBackfillResult(
            int plannedTaskCount,
            List<Long> plannedTaskIds,
            List<Long> gapIds,
            List<String> categories,
            String message
    ) {
        this.plannedTaskCount = plannedTaskCount;
        this.plannedTaskIds = plannedTaskIds == null ? List.of() : List.copyOf(plannedTaskIds);
        this.gapIds = gapIds == null ? List.of() : List.copyOf(gapIds);
        this.categories = categories == null ? List.of() : List.copyOf(categories);
        this.message = message;
    }

    public int getPlannedTaskCount() {
        return plannedTaskCount;
    }

    public List<Long> getPlannedTaskIds() {
        return plannedTaskIds;
    }

    public List<Long> getGapIds() {
        return gapIds;
    }

    public List<String> getCategories() {
        return categories;
    }

    public String getMessage() {
        return message;
    }
}
