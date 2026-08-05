package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleReconcilerTest {

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
    void explicitCutoverAnchorCreatesTodaysAlreadyDueSlotAndPersistsUtc() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        ScheduleReconciler reconciler = reconciler(store);

        List<DataPullTask> tasks = reconciler.reconcile(atShanghai("2026-08-02T04:00:00"));

        assertEquals(1, tasks.size());
        assertEquals(LocalDateTime.of(2026, 8, 1, 19, 0), tasks.get(0).getScheduleSlot());
        assertEquals(307L, tasks.get(0).getOwnerUserId());
        assertEquals(108065L, tasks.get(0).getLogicalStoreId());
        assertEquals("account-307", tasks.get(0).getAccountKey());
        assertEquals("egress-cn-1", tasks.get(0).getEgressKey());
        assertEquals("PRJ108065", tasks.get(0).getProjectCode());
        assertEquals("STR108065-NSA", tasks.get(0).getStoreCode());
        assertEquals("SA", tasks.get(0).getSiteCode());
        assertEquals(SCOPE.getStableScopeKey(), tasks.get(0).getScopeKey());
        assertEquals(
                LocalDateTime.of(2026, 8, 1, 19, 0),
                store.latestScheduleSlot(OperationCode.DP04, SCOPE.getStableScopeKey()).orElseThrow()
        );
    }

    @Test
    void currentCatchUpKeepsOnlyTheLatestNeverStartedSlotAndIsRestartIdempotent() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        ScheduleReconciler reconciler = reconciler(store);
        DataPullTask initial = reconciler.reconcile(atShanghai("2026-08-02T04:00:00")).get(0);

        List<DataPullTask> catchUp = reconciler.reconcile(atShanghai("2026-08-05T04:00:00"));
        List<DataPullTask> repeated = reconciler.reconcile(atShanghai("2026-08-05T04:00:00"));

        assertEquals(1, catchUp.size());
        assertEquals(
                List.of(LocalDateTime.of(2026, 8, 4, 19, 0)),
                catchUp.stream().map(DataPullTask::getScheduleSlot).collect(java.util.stream.Collectors.toList())
        );
        assertEquals(List.of(), repeated);
        assertEquals(TaskState.SUPERSEDED, store.find(initial.getId()).orElseThrow().getState());
        assertEquals(1, store.dueCandidates(
                LocalDateTime.of(2026, 8, 5, 0, 0),
                10
        ).size());
    }

    @Test
    void duplicateActiveScopeBlocksTheOperationBeforeAnyTaskInsert() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullScope other = new DataPullScope(
                308L,
                108066L,
                "account-308",
                "egress-cn-2",
                "PRJ108066",
                "STR108066-NSA",
                "SA",
                "owner=308|store=STR108066-NSA|site=SA"
        );
        TestDataPullJob job = new TestDataPullJob(
                OperationCode.DP04,
                "noon-partner",
                List.of(SCOPE, SCOPE, other),
                ignored -> AdvanceResult.succeeded()
        );
        ScheduleReconciler reconciler = new ScheduleReconciler(
                new DataPullScheduleRegistry(),
                new DataPullJobRegistry(List.of(job)),
                store,
                anchorStore(OperationCode.DP04, List.of(SCOPE)),
                admissionStore(List.of(SCOPE))
        );

        assertThrows(
                IllegalStateException.class,
                () -> reconciler.reconcile(atShanghai("2026-08-02T04:00:00"))
        );
        assertEquals(
                List.of(),
                store.dueCandidates(LocalDateTime.of(2026, 8, 1, 20, 0), 10)
        );
    }

    @Test
    void oneInvalidOperationIsTypedAndDoesNotSuppressOtherOperations() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        TestDataPullJob healthy = new TestDataPullJob(
                OperationCode.DP02,
                "noon-report",
                List.of(SCOPE),
                ignored -> AdvanceResult.succeeded()
        );
        TestDataPullJob invalid = new TestDataPullJob(
                OperationCode.DP04,
                "noon-product",
                List.of(SCOPE, SCOPE),
                ignored -> AdvanceResult.succeeded()
        );
        InMemoryDataPullScheduleAnchorStore anchors = new InMemoryDataPullScheduleAnchorStore();
        activate(anchors, OperationCode.DP02);
        activate(anchors, OperationCode.DP04);
        ScheduleReconciler reconciler = new ScheduleReconciler(
                new DataPullScheduleRegistry(),
                new DataPullJobRegistry(List.of(healthy, invalid)),
                store,
                anchors,
                admissionStore(List.of(SCOPE))
        );

        ScheduleReconciliationOutcome outcome = reconciler.reconcileOperations(
                atShanghai("2026-08-02T10:00:00")
        );

        assertEquals(1, outcome.getReconciledTaskCount());
        assertEquals(2, outcome.getOperations().size());
        assertEquals(false, outcome.getOperations().get(0).isFailed());
        assertEquals(true, outcome.getOperations().get(1).isFailed());
        assertEquals(
                ScheduleReconciliationOutcome.SANITIZED_FAILURE_CODE,
                outcome.getOperations().get(1).getFailureCode()
        );
        assertEquals(
                LocalDateTime.of(2026, 8, 2, 0, 30),
                store.latestScheduleSlot(
                        OperationCode.DP02,
                        SCOPE.getStableScopeKey()
                ).orElseThrow()
        );
        assertEquals(
                java.util.Optional.empty(),
                store.latestScheduleSlot(OperationCode.DP04, SCOPE.getStableScopeKey())
        );
    }

    @Test
    void interruptedReconciliationRethrowsTheOriginalFailureImmediately() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        TestDataPullJob invalid = new TestDataPullJob(
                OperationCode.DP04,
                "noon-product",
                List.of(SCOPE, SCOPE),
                ignored -> AdvanceResult.succeeded()
        );
        ScheduleReconciler reconciler = new ScheduleReconciler(
                new DataPullScheduleRegistry(),
                new DataPullJobRegistry(List.of(invalid)),
                store,
                anchorStore(OperationCode.DP04, List.of(SCOPE)),
                admissionStore(List.of(SCOPE))
        );

        Thread.currentThread().interrupt();
        try {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> reconciler.reconcile(atShanghai("2026-08-02T10:00:00"))
            );
            assertEquals("DP_RUNTIME_INTERRUPTED", failure.getMessage());
        } finally {
            Thread.interrupted();
        }
    }

    private ScheduleReconciler reconciler(InMemoryDataPullTaskStore store) {
        TestDataPullJob job = new TestDataPullJob(
                OperationCode.DP04,
                "noon-partner",
                List.of(SCOPE),
                ignored -> AdvanceResult.succeeded()
        );
        return new ScheduleReconciler(
                new DataPullScheduleRegistry(),
                new DataPullJobRegistry(List.of(job)),
                store,
                anchorStore(OperationCode.DP04, List.of(SCOPE)),
                admissionStore(List.of(SCOPE))
        );
    }

    private InMemoryDataPullScheduleAnchorStore anchorStore(
            OperationCode operation,
            List<DataPullScope> scopes
    ) {
        LocalDateTime createdAtUtc = LocalDateTime.of(2026, 8, 1, 16, 0);
        LocalDateTime reconcileAfterUtc = createdAtUtc.minusNanos(1_000_000L);
        List<DataPullScheduleAnchor> anchors = scopes.stream()
                .map(scope -> {
                    DataPullScopeAdmission admission = cutoverAdmission(
                            scope,
                            createdAtUtc
                    );
                    return DataPullScheduleAnchor.cutover(
                            operation, admission, reconcileAfterUtc, createdAtUtc,
                            "a".repeat(64)
                    );
                })
                .collect(java.util.stream.Collectors.toList());
        InMemoryDataPullScheduleAnchorStore result = new InMemoryDataPullScheduleAnchorStore();
        result.activate(
                operation,
                "test-cutover-20260802",
                anchors,
                createdAtUtc
        );
        return result;
    }

    private void activate(
            InMemoryDataPullScheduleAnchorStore anchors,
            OperationCode operation
    ) {
        LocalDateTime activatedAtUtc = LocalDateTime.of(2026, 8, 1, 16, 0);
        DataPullScopeAdmission admission = cutoverAdmission(SCOPE, activatedAtUtc);
        anchors.activate(
                operation,
                "test-cutover-20260802",
                List.of(DataPullScheduleAnchor.cutover(
                        operation,
                        admission,
                        activatedAtUtc.minusNanos(1_000_000L),
                        activatedAtUtc,
                        "f".repeat(64)
                )),
                activatedAtUtc
        );
    }

    private InMemoryDataPullScopeAdmissionStore admissionStore(
            List<DataPullScope> scopes
    ) {
        LocalDateTime admittedAtUtc = LocalDateTime.of(2026, 8, 1, 16, 0);
        DataPullScopeAdmission[] admissions = scopes.stream()
                .map(scope -> cutoverAdmission(scope, admittedAtUtc))
                .toArray(DataPullScopeAdmission[]::new);
        return new InMemoryDataPullScopeAdmissionStore(admissions);
    }

    private DataPullScopeAdmission cutoverAdmission(
            DataPullScope scope,
            LocalDateTime admittedAtUtc
    ) {
        return DataPullScopeAdmission.cutoverExisting(
                scope,
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
