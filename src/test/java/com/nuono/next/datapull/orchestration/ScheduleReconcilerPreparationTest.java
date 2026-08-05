package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.AdmittedDataPullScope;
import com.nuono.next.datapull.schedule.DataPullSchedule;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchor;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorStore;
import com.nuono.next.datapull.schedule.DataPullScheduleRegistry;
import com.nuono.next.datapull.schedule.DataPullScopeAdmission;
import com.nuono.next.datapull.schedule.DataPullScopeAdmissionStore;
import com.nuono.next.datapull.schedule.InMemoryDataPullScheduleAnchorStore;
import com.nuono.next.datapull.schedule.InMemoryDataPullScopeAdmissionStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleReconcilerPreparationTest {
    private static final DataPullScope SCOPE = new DataPullScope(
            "NOON", 307L, 108065L, "account-307", "egress-1",
            "PRJ108065", "STR108065-NSA", "SA", "scope-307"
    );
    private static final LocalDateTime CUTOVER_AT = LocalDateTime.of(2026, 8, 1, 16, 0);

    @Test
    void admissionBindingAnchorAndSlotPreparationUseOneCapturedCohortInOrder() {
        List<String> events = new ArrayList<>();
        RecordingJob job = new RecordingJob(events, false);
        InMemoryDataPullTaskStore tasks = new InMemoryDataPullTaskStore();
        ScheduleReconciler reconciler = reconciler(job, tasks, events);

        List<DataPullTask> created = reconciler.reconcile(
                atShanghai("2026-08-02T09:00:00")
        );

        assertEquals(1, created.size());
        assertEquals(1, job.sourceReads);
        assertEquals(List.of("admission", "binding", "anchor", "slot-binding"), events);
    }

    @Test
    void slotBindingFailureOccursBeforeAnyTaskInsert() {
        List<String> events = new ArrayList<>();
        RecordingJob job = new RecordingJob(events, true);
        InMemoryDataPullTaskStore tasks = new InMemoryDataPullTaskStore();
        ScheduleReconciler reconciler = reconciler(job, tasks, events);

        assertThrows(
                IllegalStateException.class,
                () -> reconciler.reconcile(atShanghai("2026-08-02T09:00:00"))
        );

        assertEquals(List.of(), tasks.dueCandidates(LocalDateTime.of(2026, 8, 2, 2, 0), 10));
        assertEquals(List.of("admission", "binding", "anchor", "slot-binding"), events);
    }

    private static ScheduleReconciler reconciler(
            RecordingJob job,
            InMemoryDataPullTaskStore tasks,
            List<String> events
    ) {
        InMemoryDataPullScopeAdmissionStore admissions =
                new InMemoryDataPullScopeAdmissionStore(admission());
        DataPullScopeAdmissionStore recordingAdmissions = new DataPullScopeAdmissionStore() {
            @Override
            public List<AdmittedDataPullScope> admitCurrent(
                    OperationCode operation, List<DataPullScope> scopes
            ) {
                events.add("admission");
                return admissions.admitCurrent(operation, scopes);
            }

            @Override
            public List<AdmittedDataPullScope> requireActiveAdmissions(
                    OperationCode operation, List<DataPullScope> scopes
            ) {
                return admissions.requireActiveAdmissions(operation, scopes);
            }
        };
        InMemoryDataPullScheduleAnchorStore anchors = anchors();
        DataPullScheduleAnchorStore recordingAnchors = operation -> {
            events.add("anchor");
            return anchors.open(operation);
        };
        return new ScheduleReconciler(
                new DataPullScheduleRegistry(),
                new DataPullJobRegistry(List.of(job)),
                tasks,
                recordingAnchors,
                recordingAdmissions
        );
    }

    private static InMemoryDataPullScheduleAnchorStore anchors() {
        DataPullScheduleAnchor anchor = DataPullScheduleAnchor.cutover(
                OperationCode.DP02,
                admission(),
                CUTOVER_AT.minusNanos(1_000_000),
                CUTOVER_AT,
                "a".repeat(64)
        );
        InMemoryDataPullScheduleAnchorStore result = new InMemoryDataPullScheduleAnchorStore();
        result.activate(
                OperationCode.DP02, "cutover-20260801", List.of(anchor), CUTOVER_AT
        );
        return result;
    }

    private static DataPullScopeAdmission admission() {
        return DataPullScopeAdmission.cutoverExisting(
                SCOPE, "cutover-20260801", CUTOVER_AT
        );
    }

    private static Instant atShanghai(String value) {
        return ZonedDateTime.of(
                LocalDateTime.parse(value), DataPullSchedule.ZONE_ID
        ).toInstant();
    }

    private static final class RecordingJob implements DataPullJob {
        private final List<String> events;
        private final boolean failSlotBinding;
        private int sourceReads;
        private DataPullScope captured;

        private RecordingJob(List<String> events, boolean failSlotBinding) {
            this.events = events;
            this.failSlotBinding = failSlotBinding;
        }

        @Override public OperationCode operationCode() { return OperationCode.DP02; }
        @Override public String providerChannel() { return "test"; }
        @Override public String initialStep() { return "FETCH"; }
        @Override public List<DataPullScope> listScopes() {
            throw new AssertionError("enqueue must use the captured preparation cohort");
        }

        @Override
        public DataPullScopePreparation prepareScopesForEnqueue() {
            sourceReads++;
            captured = SCOPE;
            return DataPullScopePreparation.deferred(List.of(captured), admitted -> {
                events.add("binding");
                assertSame(captured, admitted.get(0).getScope());
            });
        }

        @Override
        public List<DataPullScheduledScope> prepareTaskScopesForEnqueue(
                List<DataPullScheduledScope> scheduled,
                List<AdmittedDataPullScope> admitted
        ) {
            events.add("slot-binding");
            assertSame(captured, scheduled.get(0).getScope());
            if (failSlotBinding) {
                throw new IllegalStateException("test slot binding failure");
            }
            return scheduled;
        }

        @Override public AdvanceResult advance(ExecutionContext context) {
            return AdvanceResult.succeeded();
        }
    }
}
