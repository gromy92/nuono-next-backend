package com.nuono.next.noonsync;

import java.util.List;

public class NoonSyncAdminDiagnosticsView {

    private final List<NoonSyncAdminPlanSummary> planSummaries;
    private final List<NoonSyncAdminTaskSummary> taskSummaries;
    private final NoonSyncAdminHealthSummary health;

    public NoonSyncAdminDiagnosticsView(
            List<NoonSyncAdminPlanSummary> planSummaries,
            List<NoonSyncAdminTaskSummary> taskSummaries,
            NoonSyncAdminHealthSummary health
    ) {
        this.planSummaries = planSummaries == null ? List.of() : List.copyOf(planSummaries);
        this.taskSummaries = taskSummaries == null ? List.of() : List.copyOf(taskSummaries);
        this.health = health;
    }

    public List<NoonSyncAdminPlanSummary> getPlanSummaries() {
        return planSummaries;
    }

    public List<NoonSyncAdminTaskSummary> getTaskSummaries() {
        return taskSummaries;
    }

    public NoonSyncAdminHealthSummary getHealth() {
        return health;
    }
}
