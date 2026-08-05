package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskBatchProposal;
import com.nuono.next.datapull.persistence.DataPullTaskCatchUpMode;
import com.nuono.next.datapull.persistence.MyBatisDataPullTaskBatchStore;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleTaskPlanMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Plans and inserts at most 64 immutable tasks from one sealed epoch step. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class MyBatisScheduleTaskBatchApplier {
    private static final int LIMIT = MyBatisScheduleReconciliationStore.SCOPES_PER_STEP;
    private final DataPullScheduleScanMapper scans;
    private final DataPullScheduleTaskPlanMapper mapper;
    private final MyBatisDataPullTaskBatchStore tasks;
    private final ScheduleTaskPayloadBinderRegistry payloadBinders;

    public MyBatisScheduleTaskBatchApplier(
            DataPullScheduleScanMapper scans,
            DataPullScheduleTaskPlanMapper mapper,
            MyBatisDataPullTaskBatchStore tasks,
            ScheduleTaskPayloadBinderRegistry payloadBinders
    ) {
        this.scans = Objects.requireNonNull(scans, "scans");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.payloadBinders = Objects.requireNonNull(payloadBinders, "payloadBinders");
    }

    public List<DataPullTask> advance(
            DataPullJob job,
            DataPullSchedule schedule,
            Instant observedAt
    ) {
        DataPullJob owner = Objects.requireNonNull(job, "job");
        OperationCode operation = owner.operationCode();
        if (schedule.operationCode() != operation) {
            throw new IllegalArgumentException("schedule and job operation differ");
        }
        ScheduleSourceEpochRow epoch = scans.lockActiveEpoch(operation);
        requireSchedulingEpoch(epoch);
        ScheduleTaskPayloadBinder payloadBinder = "COMPLETE".equals(
                epoch.getBindingCloseState()
        ) ? payloadBinders.require(operation) : null;
        List<ScheduleSourceStageRow> rows = List.copyOf(Objects.requireNonNull(
                mapper.listScheduleStageAfter(
                        operation, epoch.getEpochNo(), epoch.getScheduleCursorScopeKey(), LIMIT
                ), "bounded schedule stage"
        ));
        if (rows.isEmpty()) {
            finishOrReset(epoch, mapper.findPendingScheduleAtOrBefore(
                    operation, epoch.getEpochNo(), epoch.getScheduleCursorScopeKey()
            ) != null);
            return List.of();
        }
        Map<String, LocalDateTime> latest = latestByScope(
                mapper.listLatestSlots(operation, scopeKeys(rows))
        );
        Instant upperBound = epoch.getReconcileUntilUtc().toInstant(ZoneOffset.UTC);
        List<PlannedTask> planned = new ArrayList<>();
        List<ScheduleStageProgressUpdate> updates = new ArrayList<>();
        for (ScheduleSourceStageRow row : rows) {
            if (planned.size() == LIMIT) break;
            planScope(
                    operation, schedule, row, latest.get(row.getScopeKey()), upperBound,
                    LIMIT - planned.size(), planned, updates
            );
        }
        List<DataPullTask> proposals = buildTasks(
                owner, planned, Objects.requireNonNull(observedAt, "observedAt")
        );
        if (payloadBinder != null && !proposals.isEmpty()) {
            payloadBinder.bind(
                    operation,
                    proposals,
                    mapper.listBindingsForSlots(proposals)
            );
        }
        List<DataPullTaskBatchProposal> batch = new ArrayList<>(proposals.size());
        for (int index = 0; index < proposals.size(); index++) {
            batch.add(new DataPullTaskBatchProposal(
                    proposals.get(index), catchUpMode(planned.get(index).strategy)
            ));
        }
        List<DataPullTask> stored = tasks.enqueue(batch);
        List<ScheduleStageProgressUpdate> running = new ArrayList<>();
        List<String> completed = new ArrayList<>();
        for (ScheduleStageProgressUpdate update : updates) {
            if ("RUNNING".equals(update.getScheduleState())) running.add(update);
            else completed.add(update.getScopeKey());
        }
        if (!running.isEmpty() && mapper.updateRunningScheduleStages(
                operation, epoch.getEpochNo(), running
        ) != running.size()) {
            throw new IllegalStateException("running schedule stage batch changed invalid rows");
        }
        if (!completed.isEmpty() && mapper.deleteCompletedScheduleStages(
                operation, epoch.getEpochNo(), completed
        ) != completed.size()) {
            throw new IllegalStateException("completed schedule stage batch changed invalid rows");
        }
        String nextCursor = updates.isEmpty() ? epoch.getScheduleCursorScopeKey()
                : updates.get(updates.size() - 1).getScopeKey();
        advanceHeader(epoch, nextCursor, "SCHEDULING");
        return stored;
    }

    private void planScope(
            OperationCode operation,
            DataPullSchedule schedule,
            ScheduleSourceStageRow row,
            LocalDateTime latestTaskSlot,
            Instant upperBound,
            int taskCapacity,
            List<PlannedTask> planned,
            List<ScheduleStageProgressUpdate> updates
    ) {
        LocalDateTime start = latest(row.getReconcileAfterUtc(), row.getScheduleAfterUtc());
        start = latest(start, latestTaskSlot);
        if (start == null) throw new IllegalStateException("schedule scope lacks an anchor");
        if (start.toInstant(ZoneOffset.UTC).isAfter(upperBound)) {
            updates.add(progress(row, start, "COMPLETE"));
            return;
        }
        DataPullCatchUpPlan.Strategy strategy = DataPullCatchUpPlan.strategyFor(operation);
        if (strategy == DataPullCatchUpPlan.Strategy.EXACT_WINDOWS) {
            ScheduleSlotPage page = schedule.missedSlotsPage(
                    row.getScopeKey(), start.toInstant(ZoneOffset.UTC), upperBound, taskCapacity
            );
            for (DataPullScheduleSlot slot : page.getSlots()) {
                planned.add(new PlannedTask(row, slot, strategy));
            }
            LocalDateTime after = page.getSlots().isEmpty() ? start
                    : utc(page.getSlots().get(page.getSlots().size() - 1));
            updates.add(progress(row, after, page.hasMore() ? "RUNNING" : "COMPLETE"));
            return;
        }
        Optional<DataPullScheduleSlot> latest = schedule.latestMissedSlot(
                row.getScopeKey(), start.toInstant(ZoneOffset.UTC), upperBound
        );
        if (latest.isEmpty()) {
            updates.add(progress(row, start, "COMPLETE"));
            return;
        }
        DataPullScheduleSlot slot = latest.get();
        if (strategy == DataPullCatchUpPlan.Strategy.ROLLING_DATE_UNION) {
            ScheduleSlotPage first = schedule.missedSlotsPage(
                    row.getScopeKey(), start.toInstant(ZoneOffset.UTC), upperBound, 1
            );
            if (first.getSlots().isEmpty()) {
                throw new IllegalStateException("rolling schedule lacks its first boundary");
            }
            List<DataPullScheduleSlot> boundaries = new ArrayList<>(2);
            boundaries.add(first.getSlots().get(0));
            if (!utc(slot).equals(utc(boundaries.get(0)))) boundaries.add(slot);
            slot = DataPullCatchUpPlan.from(operation, boundaries, upperBound)
                    .getTaskSlots().get(0);
        }
        planned.add(new PlannedTask(row, slot, strategy));
        updates.add(progress(row, utc(latest.get()), "COMPLETE"));
    }

    private List<DataPullTask> buildTasks(
            DataPullJob job, List<PlannedTask> planned, Instant observedAt
    ) {
        if (planned.isEmpty()) return List.of();
        List<Long> ids = tasks.allocateIds(planned.size());
        LocalDateTime now = LocalDateTime.ofInstant(observedAt, ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS);
        List<DataPullTask> result = new ArrayList<>(planned.size());
        for (int index = 0; index < planned.size(); index++) {
            PlannedTask item = planned.get(index);
            ScheduleSourceStageRow scope = item.scope;
            result.add(DataPullTask.queued(
                    ids.get(index), job.operationCode(), job.providerChannel(),
                    scope.getOwnerUserId(), scope.getLogicalStoreId(), scope.getAccountKey(),
                    scope.getEgressKey(), scope.getProjectCode(), scope.getStoreCode(),
                    scope.getSiteCode(), scope.getScopeKey(), utc(item.slot),
                    item.slot.getBusinessWindow().getKey(), job.initialStep(), now
            ));
        }
        return result;
    }

    private void finishOrReset(ScheduleSourceEpochRow epoch, boolean pendingBeforeCursor) {
        advanceHeader(epoch, pendingBeforeCursor ? null : epoch.getScheduleCursorScopeKey(),
                pendingBeforeCursor ? "SCHEDULING" : "COMPLETE");
    }

    private void advanceHeader(ScheduleSourceEpochRow epoch, String cursor, String state) {
        if (mapper.advanceSchedulePhase(
                epoch.getOperationCode(), epoch.getEpochNo(), epoch.getVersion(),
                epoch.getScheduleCursorScopeKey(), cursor, state
        ) != 1) throw new IllegalStateException("schedule phase CAS lost");
    }

    private static void requireSchedulingEpoch(ScheduleSourceEpochRow epoch) {
        if (epoch == null || !"SCHEDULING".equals(epoch.getEpochState())) {
            throw new IllegalStateException("active epoch is not scheduling");
        }
        String closeState = epoch.getBindingCloseState();
        if (!"COMPLETE".equals(closeState) && !"NOT_REQUIRED".equals(closeState)) {
            throw new IllegalStateException("schedule binding-close fence is incomplete");
        }
    }

    private static Map<String, LocalDateTime> latestByScope(List<ScheduleLatestSlotRow> values) {
        Map<String, LocalDateTime> result = new HashMap<>();
        for (ScheduleLatestSlotRow row : List.copyOf(values)) {
            if (result.containsKey(row.getScopeKey())) {
                throw new IllegalStateException("duplicate latest schedule slot");
            }
            result.put(row.getScopeKey(), row.getLatestScheduleSlot());
        }
        return result;
    }

    private static List<String> scopeKeys(List<ScheduleSourceStageRow> rows) {
        List<String> result = new ArrayList<>(rows.size());
        for (ScheduleSourceStageRow row : rows) result.add(row.getScopeKey());
        return result;
    }

    private static ScheduleStageProgressUpdate progress(
            ScheduleSourceStageRow row, LocalDateTime after, String state
    ) {
        return new ScheduleStageProgressUpdate(row.getScopeKey(), after, state);
    }

    private static DataPullTaskCatchUpMode catchUpMode(DataPullCatchUpPlan.Strategy strategy) {
        if (strategy == DataPullCatchUpPlan.Strategy.LATEST_CURRENT) {
            return DataPullTaskCatchUpMode.LATEST_CURRENT;
        }
        if (strategy == DataPullCatchUpPlan.Strategy.ROLLING_DATE_UNION) {
            return DataPullTaskCatchUpMode.ROLLING_DATE_UNION;
        }
        return null;
    }

    private static LocalDateTime latest(LocalDateTime first, LocalDateTime second) {
        if (first == null) return second;
        return second != null && second.isAfter(first) ? second : first;
    }

    private static LocalDateTime utc(DataPullScheduleSlot slot) {
        return slot.getScheduledAt().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static final class PlannedTask {
        private final ScheduleSourceStageRow scope;
        private final DataPullScheduleSlot slot;
        private final DataPullCatchUpPlan.Strategy strategy;
        private PlannedTask(
                ScheduleSourceStageRow scope,
                DataPullScheduleSlot slot,
                DataPullCatchUpPlan.Strategy strategy
        ) {
            this.scope=scope; this.slot=slot; this.strategy=strategy;
        }
    }
}
