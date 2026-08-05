package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.DataPullSchedule;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchor;
import com.nuono.next.datapull.schedule.DataPullScheduleRegistry;
import com.nuono.next.datapull.schedule.DataPullScopeAdmission;
import com.nuono.next.datapull.schedule.InMemoryDataPullScheduleAnchorStore;
import com.nuono.next.datapull.schedule.InMemoryDataPullScopeAdmissionStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ScheduleReconcilerAnchorTest {

    private static final DataPullScope SCOPE = new DataPullScope(
            307L,
            108065L,
            "account-307",
            "PRJ108065",
            "STR108065-NSA",
            "SA",
            "owner=307|store=STR108065-NSA|site=SA"
    );

    @Test
    void missingPersistentAnchorStoreFailsClosedWithoutCreatingTasks() {
        InMemoryDataPullTaskStore tasks = new InMemoryDataPullTaskStore();
        ScheduleReconciler reconciler = new ScheduleReconciler(
                schedules(), jobs(), tasks
        );

        assertThrows(
                IllegalStateException.class,
                () -> reconciler.reconcile(atShanghai("2026-08-05T10:00:00"))
        );
        assertEquals(
                List.of(),
                tasks.dueCandidates(LocalDateTime.of(2026, 8, 5, 2, 0), 10)
        );
    }

    @Test
    void explicitCutoverAnchorCatchesEveryCrossDaySlotAndRestartIsIdempotent() {
        InMemoryDataPullTaskStore tasks = new InMemoryDataPullTaskStore();
        InMemoryDataPullScheduleAnchorStore anchors = activatedWithExistingScope();
        ScheduleReconciler reconciler = reconciler(
                tasks,
                anchors,
                new InMemoryDataPullScopeAdmissionStore(cutoverAdmission())
        );

        List<DataPullTask> created = reconciler.reconcile(
                atShanghai("2026-08-05T10:00:00")
        );
        List<DataPullTask> repeated = reconciler(
                tasks,
                anchors,
                new InMemoryDataPullScopeAdmissionStore(cutoverAdmission())
        ).reconcile(
                atShanghai("2026-08-05T10:00:00")
        );

        assertEquals(4, created.size());
        assertEquals(
                List.of(
                        "DP02:date-range:2026-08-01..2026-08-01",
                        "DP02:date-range:2026-08-02..2026-08-02",
                        "DP02:date-range:2026-08-03..2026-08-03",
                        "DP02:date-range:2026-08-04..2026-08-04"
                ),
                created.stream()
                        .map(DataPullTask::getBusinessWindowKey)
                        .collect(Collectors.toList())
        );
        assertEquals(List.of(), repeated);
    }

    @Test
    void midDayPostCutoverScopeNeverBackfillsSlotsBeforePersistedEligibility() {
        InMemoryDataPullTaskStore tasks = new InMemoryDataPullTaskStore();
        InMemoryDataPullScheduleAnchorStore anchors = new InMemoryDataPullScheduleAnchorStore();
        anchors.activate(
                OperationCode.DP02,
                "cutover-20260802",
                List.of(),
                LocalDateTime.of(2026, 8, 2, 2, 0)
        );
        DataPullScopeAdmission admission = DataPullScopeAdmission.postCutover(
                SCOPE,
                LocalDateTime.of(2026, 8, 5, 2, 0),
                "cutover-20260802",
                LocalDateTime.of(2026, 8, 5, 2, 0)
        );
        InMemoryDataPullScopeAdmissionStore admissions =
                new InMemoryDataPullScopeAdmissionStore(admission);
        ScheduleReconciler reconciler = reconciler(tasks, anchors, admissions);

        List<DataPullTask> created = reconciler.reconcile(
                atShanghai("2026-08-06T10:00:00")
        );
        List<DataPullTask> repeated = reconciler.reconcile(
                atShanghai("2026-08-06T10:00:00")
        );

        assertEquals(1, created.size());
        assertEquals(
                "DP02:date-range:2026-08-05..2026-08-05",
                created.get(0).getBusinessWindowKey()
        );
        assertEquals(List.of(), repeated);
    }

    private ScheduleReconciler reconciler(
            InMemoryDataPullTaskStore tasks,
            InMemoryDataPullScheduleAnchorStore anchors,
            InMemoryDataPullScopeAdmissionStore admissions
    ) {
        return new ScheduleReconciler(schedules(), jobs(), tasks, anchors, admissions);
    }

    private DataPullScheduleRegistry schedules() {
        return new DataPullScheduleRegistry();
    }

    private DataPullJobRegistry jobs() {
        return new DataPullJobRegistry(List.of(new TestDataPullJob(
                OperationCode.DP02,
                "noon-partner",
                List.of(SCOPE),
                ignored -> AdvanceResult.succeeded()
        )));
    }

    private InMemoryDataPullScheduleAnchorStore activatedWithExistingScope() {
        LocalDateTime activatedAt = LocalDateTime.of(2026, 8, 2, 2, 0);
        DataPullScopeAdmission admission = cutoverAdmission();
        DataPullScheduleAnchor anchor = DataPullScheduleAnchor.cutover(
                OperationCode.DP02,
                admission,
                LocalDateTime.of(2026, 8, 1, 15, 59, 59, 999_000_000),
                activatedAt,
                "c".repeat(64)
        );
        InMemoryDataPullScheduleAnchorStore store = new InMemoryDataPullScheduleAnchorStore();
        store.activate(
                OperationCode.DP02,
                "cutover-20260802",
                List.of(anchor),
                activatedAt
        );
        return store;
    }

    private DataPullScopeAdmission cutoverAdmission() {
        return DataPullScopeAdmission.cutoverExisting(
                SCOPE,
                "cutover-20260802",
                LocalDateTime.of(2026, 8, 2, 1, 55)
        );
    }

    private Instant atShanghai(String localDateTime) {
        return ZonedDateTime.of(
                LocalDateTime.parse(localDateTime),
                DataPullSchedule.ZONE_ID
        ).toInstant();
    }
}
