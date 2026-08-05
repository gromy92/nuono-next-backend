package com.nuono.next.datapull.persistence;

import com.nuono.next.datapull.runtime.TaskState;
import java.util.Map;

/** Test adapter helper mirroring the production unstarted-claim CAS. */
final class InMemoryUnstartedClaimRelease {
    private InMemoryUnstartedClaimRelease() { }

    static boolean apply(Map<Long, DataPullTask> tasks, DataPullUnstartedClaimRelease release) {
        DataPullTask task = tasks.get(release.getTaskId());
        if (!DataPullTaskExecutionPolicy.ownsLiveEpoch(
                task,
                release.getExpectedFenceEpoch(),
                release.getExpectedVersion(),
                release.getLeaseOwner(),
                release.getNow()
        )) return false;
        task.setState(TaskState.QUEUED);
        task.setLeaseOwner(null);
        task.setLeaseUntil(null);
        task.setVersion(Math.addExact(task.getVersion(), 1L));
        task.setUpdatedAt(release.getNow());
        return true;
    }
}
