package com.nuono.next.sales;

import java.util.List;

public class ProductLifecycleStateOverview {

    private final ProductLifecycleCurrentState currentState;
    private final List<ProductLifecycleHistoryRecord> history;
    private final List<ProductLifecycleJobRecord> jobs;

    public ProductLifecycleStateOverview(
            ProductLifecycleCurrentState currentState,
            List<ProductLifecycleHistoryRecord> history,
            List<ProductLifecycleJobRecord> jobs
    ) {
        this.currentState = currentState;
        this.history = history == null ? List.of() : List.copyOf(history);
        this.jobs = jobs == null ? List.of() : List.copyOf(jobs);
    }

    public ProductLifecycleCurrentState getCurrentState() {
        return currentState;
    }

    public List<ProductLifecycleHistoryRecord> getHistory() {
        return history;
    }

    public List<ProductLifecycleJobRecord> getJobs() {
        return jobs;
    }
}
