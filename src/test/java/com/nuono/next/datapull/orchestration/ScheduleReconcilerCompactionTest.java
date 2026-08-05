package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.datapull.schedule.DataPullSchedule;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchor;
import com.nuono.next.datapull.schedule.DataPullScheduleRegistry;
import com.nuono.next.datapull.schedule.DataPullScopeAdmission;
import com.nuono.next.datapull.schedule.InMemoryDataPullScheduleAnchorStore;
import com.nuono.next.datapull.schedule.InMemoryDataPullScopeAdmissionStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ScheduleReconcilerCompactionTest {

    private static final DataPullScope SCOPE = new DataPullScope(
            307L,
            108065L,
            "account-307",
            "egress-cn-1",
            "PRJ108065",
            "STR108065-NSA",
            "SA",
            "owner=307|store=STR108065-NSA|site=SA"
    );

    @Test
    void claimedCurrentTaskIsPreservedAndRunsBeforeLatestReplacement() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        ScheduleReconciler reconciler = reconciler(store, OperationCode.DP04);
        DataPullTask initial = reconciler.reconcile(atShanghai("2026-08-02T04:00:00")).get(0);
        LocalDateTime claimedAt = initial.getScheduleSlot().plusMinutes(1);
        DataPullTask claimed = store.claim(
                initial.getId(), initial.getVersion(), "worker-a", claimedAt.plusMinutes(1), claimedAt
        ).orElseThrow();

        DataPullTask latest = reconciler.reconcile(atShanghai("2026-08-05T04:00:00")).get(0);
        LocalDateTime catchUpNow = LocalDateTime.ofInstant(
                atShanghai("2026-08-05T04:00:00"), ZoneOffset.UTC
        );

        assertEquals(TaskState.RUNNING, store.find(claimed.getId()).orElseThrow().getState());
        assertEquals(TaskState.QUEUED, store.find(latest.getId()).orElseThrow().getState());
        assertEquals(
                List.of(claimed.getId()),
                store.dueCandidates(catchUpNow, 10).stream()
                        .map(DataPullTask::getId)
                        .collect(Collectors.toList())
        );
    }

    @Test
    void dp01RollingBacklogBecomesOneDateUnion() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        ScheduleReconciler reconciler = reconciler(store, OperationCode.DP01);
        DataPullTask initial = reconciler.reconcile(atShanghai("2026-08-02T21:00:00")).get(0);

        List<DataPullTask> catchUp = reconciler.reconcile(atShanghai("2026-08-05T21:00:00"));

        assertEquals(1, catchUp.size());
        assertEquals(
                "DP01:date-range:2026-07-03..2026-08-04",
                catchUp.get(0).getBusinessWindowKey()
        );
        assertEquals(TaskState.SUPERSEDED, store.find(initial.getId()).orElseThrow().getState());
    }

    @Test
    void dp02CatchUpKeepsThreeExactDailyWindows() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        ScheduleReconciler reconciler = reconciler(store, OperationCode.DP02);
        reconciler.reconcile(atShanghai("2026-08-02T09:00:00"));

        List<DataPullTask> catchUp = reconciler.reconcile(atShanghai("2026-08-05T09:00:00"));

        assertEquals(3, catchUp.size());
        assertEquals(
                List.of(
                        "DP02:date-range:2026-08-02..2026-08-02",
                        "DP02:date-range:2026-08-03..2026-08-03",
                        "DP02:date-range:2026-08-04..2026-08-04"
                ),
                catchUp.stream()
                        .map(DataPullTask::getBusinessWindowKey)
                        .collect(Collectors.toList())
        );
        assertTrue(catchUp.stream().allMatch(task -> task.getState() == TaskState.QUEUED));
    }

    private ScheduleReconciler reconciler(
            InMemoryDataPullTaskStore store,
            OperationCode operation
    ) {
        TestDataPullJob job = new TestDataPullJob(
                operation,
                "provider-" + operation.name(),
                List.of(SCOPE),
                ignored -> AdvanceResult.succeeded()
        );
        return new ScheduleReconciler(
                new DataPullScheduleRegistry(),
                new DataPullJobRegistry(List.of(job)),
                store,
                anchorStore(operation),
                admissionStore()
        );
    }

    private InMemoryDataPullScheduleAnchorStore anchorStore(OperationCode operation) {
        LocalDateTime createdAtUtc = LocalDateTime.of(2026, 8, 1, 16, 0);
        DataPullScopeAdmission admission = admission(createdAtUtc);
        DataPullScheduleAnchor anchor = DataPullScheduleAnchor.cutover(
                operation,
                admission,
                createdAtUtc.minusNanos(1_000_000L),
                createdAtUtc,
                "b".repeat(64)
        );
        InMemoryDataPullScheduleAnchorStore result = new InMemoryDataPullScheduleAnchorStore();
        result.activate(
                operation,
                "test-cutover-20260802",
                List.of(anchor),
                createdAtUtc
        );
        return result;
    }

    private InMemoryDataPullScopeAdmissionStore admissionStore() {
        return new InMemoryDataPullScopeAdmissionStore(
                admission(LocalDateTime.of(2026, 8, 1, 16, 0))
        );
    }

    private DataPullScopeAdmission admission(LocalDateTime admittedAtUtc) {
        return DataPullScopeAdmission.cutoverExisting(
                SCOPE,
                "test-cutover-20260802",
                admittedAtUtc
        );
    }

    private Instant atShanghai(String localDateTime) {
        return ZonedDateTime.of(
                LocalDateTime.parse(localDateTime),
                DataPullSchedule.ZONE_ID
        ).toInstant();
    }
}
