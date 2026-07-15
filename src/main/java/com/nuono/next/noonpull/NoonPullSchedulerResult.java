package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NoonPullSchedulerResult {
    private int scannedPlanCount;
    private int createdTaskCount;
    private int skippedPlanCount;
    private int maintenanceSkippedPlanCount;
    private final List<NoonPullTaskRecord> createdTasks = new ArrayList<>();

    public void scanned() {
        scannedPlanCount++;
    }

    public void created() {
        createdTaskCount++;
    }

    public void created(NoonPullTaskRecord task) {
        created();
        if (task != null) {
            createdTasks.add(task.copy());
        }
    }

    public void skipped() {
        skippedPlanCount++;
    }

    public void maintenanceSkipped() {
        maintenanceSkippedPlanCount++;
        skipped();
    }

    public int getScannedPlanCount() {
        return scannedPlanCount;
    }

    public int getCreatedTaskCount() {
        return createdTaskCount;
    }

    public int getSkippedPlanCount() {
        return skippedPlanCount;
    }

    public int getMaintenanceSkippedPlanCount() {
        return maintenanceSkippedPlanCount;
    }

    public List<NoonPullTaskRecord> getCreatedTasks() {
        return Collections.unmodifiableList(createdTasks);
    }
}
