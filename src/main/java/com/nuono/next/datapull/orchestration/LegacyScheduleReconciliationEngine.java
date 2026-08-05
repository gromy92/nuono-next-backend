package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskCatchUpMode;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.schedule.AdmittedDataPullScope;
import com.nuono.next.datapull.schedule.DataPullCatchUpPlan;
import com.nuono.next.datapull.schedule.DataPullSchedule;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorStore;
import com.nuono.next.datapull.schedule.DataPullScheduleSlot;
import com.nuono.next.datapull.schedule.DataPullScopeAdmissionStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compatibility path retained for isolated tests until the bounded DB Adapter is supplied. */
final class LegacyScheduleReconciliationEngine {
    private final DataPullTaskStore store;
    private final DataPullScheduleAnchorStore anchors;
    private final DataPullScopeAdmissionStore admissions;

    LegacyScheduleReconciliationEngine(
            DataPullTaskStore store,
            DataPullScheduleAnchorStore anchors,
            DataPullScopeAdmissionStore admissions
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.anchors = Objects.requireNonNull(anchors, "anchors");
        this.admissions = Objects.requireNonNull(admissions, "admissions");
    }

    List<DataPullTask> reconcile(
            DataPullJob job,
            DataPullSchedule schedule,
            Instant now,
            LocalDateTime nowUtc
    ) {
        List<DataPullTask> reconciled = new ArrayList<>();
        DataPullRuntimeCancellation.requireActive();
        DataPullScopePreparation preparation = Objects.requireNonNull(
                job.prepareScopesForEnqueue(), "prepared job scope cohort"
        );
        List<DataPullScope> sources = preparation.getScopes();
        List<AdmittedDataPullScope> admitted = admissions.admitCurrent(
                job.operationCode(), sources
        );
        preparation.completeAfterAdmission(admitted);
        DataPullScheduleAnchorStore.Cohort anchorCohort = anchors.open(job.operationCode());
        List<AnchoredScope> anchored = new ArrayList<>(admitted.size());
        for (AdmittedDataPullScope admittedScope : admitted) {
            DataPullRuntimeCancellation.requireActive();
            AdmittedDataPullScope value = Objects.requireNonNull(
                    admittedScope, "admitted scope"
            );
            anchored.add(new AnchoredScope(
                    value.getScope(), anchorCohort.reconcileAfterUtc(value)
            ));
        }
        List<DataPullScheduledScope> planned = new ArrayList<>();
        for (AnchoredScope scope : anchored) {
            planScope(job, schedule, scope.scope, scope.anchorUtc, now, planned);
        }
        List<DataPullScheduledScope> prepared = requirePreparedSubset(
                planned, job.prepareTaskScopesForEnqueue(List.copyOf(planned), admitted)
        );
        for (DataPullScheduledScope scheduled : prepared) {
            reconciled.add(enqueue(
                    job, scheduled.getScope(), scheduled.getSlot(),
                    scheduled.getCatchUpStrategy(), nowUtc
            ));
        }
        DataPullRuntimeCancellation.requireActive();
        return List.copyOf(reconciled);
    }

    private void planScope(
            DataPullJob job,
            DataPullSchedule schedule,
            DataPullScope scope,
            LocalDateTime anchorUtc,
            Instant now,
            List<DataPullScheduledScope> planned
    ) {
        String scopeKey = scope.getStableScopeKey();
        LocalDateTime exclusiveStartUtc = store.latestScheduleSlot(
                job.operationCode(), scopeKey
        ).map(latest -> latest.isAfter(anchorUtc) ? latest : anchorUtc).orElse(anchorUtc);
        Instant exclusiveStart = exclusiveStartUtc.toInstant(ZoneOffset.UTC);
        if (exclusiveStart.isAfter(now)) return;
        DataPullCatchUpPlan catchUp = DataPullCatchUpPlan.from(
                job.operationCode(), schedule.missedSlots(scopeKey, exclusiveStart, now), now
        );
        for (DataPullScheduleSlot slot : catchUp.getTaskSlots()) {
            DataPullRuntimeCancellation.requireActive();
            planned.add(new DataPullScheduledScope(scope, slot, catchUp.getStrategy()));
        }
    }

    private static List<DataPullScheduledScope> requirePreparedSubset(
            List<DataPullScheduledScope> planned,
            List<DataPullScheduledScope> preparedValues
    ) {
        List<DataPullScheduledScope> prepared = List.copyOf(
                Objects.requireNonNull(preparedValues, "prepared task scopes")
        );
        int plannedIndex = 0;
        for (DataPullScheduledScope value : prepared) {
            DataPullScheduledScope scheduled = Objects.requireNonNull(value, "prepared task scope");
            while (plannedIndex < planned.size() && planned.get(plannedIndex) != scheduled) {
                plannedIndex++;
            }
            if (plannedIndex == planned.size()) {
                throw new IllegalStateException(
                        "slot preparation must preserve the planned cohort and order"
                );
            }
            plannedIndex++;
        }
        return prepared;
    }

    private DataPullTask enqueue(
            DataPullJob job,
            DataPullScope scope,
            DataPullScheduleSlot slot,
            DataPullCatchUpPlan.Strategy catchUpStrategy,
            LocalDateTime nowUtc
    ) {
        if (slot.getOperationCode() != job.operationCode()) {
            throw new IllegalStateException("schedule returned a slot for the wrong operation");
        }
        DataPullTask task = DataPullTask.queued(
                store.nextTaskId(), job.operationCode(), job.providerChannel(),
                scope.getOwnerUserId(), scope.getLogicalStoreId(), scope.getAccountKey(),
                scope.getEgressKey(), scope.getProjectCode(), scope.getStoreCode(),
                scope.getSiteCode(), scope.getStableScopeKey(),
                LocalDateTime.ofInstant(slot.getScheduledAt().toInstant(), ZoneOffset.UTC),
                slot.getBusinessWindow().getKey(), job.initialStep(), nowUtc
        );
        if (catchUpStrategy == DataPullCatchUpPlan.Strategy.LATEST_CURRENT) {
            return store.enqueueCatchUp(task, DataPullTaskCatchUpMode.LATEST_CURRENT, nowUtc);
        }
        if (catchUpStrategy == DataPullCatchUpPlan.Strategy.ROLLING_DATE_UNION) {
            return store.enqueueCatchUp(task, DataPullTaskCatchUpMode.ROLLING_DATE_UNION, nowUtc);
        }
        return store.enqueue(task);
    }

    private static final class AnchoredScope {
        private final DataPullScope scope;
        private final LocalDateTime anchorUtc;

        private AnchoredScope(DataPullScope scope, LocalDateTime anchorUtc) {
            this.scope = Objects.requireNonNull(scope, "scope");
            this.anchorUtc = Objects.requireNonNull(anchorUtc, "anchorUtc");
        }
    }
}
