package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import com.nuono.next.datapull.scope.DataPullScopeBindingEpoch;
import com.nuono.next.datapull.scope.ScheduleBindingCloseCommand;
import com.nuono.next.infrastructure.mapper.DataPullScheduleApplyMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeBindingMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** DP08-only batch Adapter; missing closes start only after the sealed present pass finishes. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class MyBatisScheduleBindingBatchApplier {
    private static final int LIMIT = MyBatisScheduleReconciliationStore.SCOPES_PER_STEP;
    private final DataPullScheduleScanMapper scans;
    private final DataPullScheduleApplyMapper apply;
    private final DataPullScopeBindingMapper bindings;

    public MyBatisScheduleBindingBatchApplier(
            DataPullScheduleScanMapper scans,
            DataPullScheduleApplyMapper apply,
            DataPullScopeBindingMapper bindings
    ) {
        this.scans = Objects.requireNonNull(scans, "scans");
        this.apply = Objects.requireNonNull(apply, "apply");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
    }

    public void advancePresent(OperationCode operation) {
        requireDp08(operation);
        ScheduleSourceEpochRow epoch = requireEpoch(operation, "BINDING_PRESENT");
        List<ScheduleSourceStageRow> page = List.copyOf(Objects.requireNonNull(
                apply.listBindingStageAfter(
                        operation, epoch.getEpochNo(), epoch.getBindingCursorScopeKey(), LIMIT + 1
                ), "bounded binding stage"
        ));
        boolean hasMore = page.size() > LIMIT;
        List<ScheduleSourceStageRow> rows = first(page);
        LocalDateTime now = lockOperationNow(operation);
        if (!rows.isEmpty()) reconcilePresent(operation, rows, now);
        String cursor = rows.isEmpty() ? epoch.getBindingCursorScopeKey()
                : rows.get(rows.size() - 1).getScopeKey();
        requireOne(apply.advanceBindingPresentPhase(
                operation, epoch.getEpochNo(), epoch.getVersion(),
                epoch.getBindingCursorScopeKey(), cursor,
                hasMore ? "BINDING_PRESENT" : "BINDING_MISSING"
        ), "binding-present phase CAS");
    }

    public void advanceMissing(OperationCode operation) {
        requireDp08(operation);
        ScheduleSourceEpochRow epoch = requireEpoch(operation, "BINDING_MISSING");
        if (!"RUNNING".equals(epoch.getBindingCloseState())) {
            throw new IllegalStateException("binding close lacks a complete-cohort fence");
        }
        LocalDateTime now = lockOperationNow(operation);
        List<DataPullScopeBindingEpoch> page = List.copyOf(Objects.requireNonNull(
                bindings.lockMissingOpenBindingsAfter(
                        operation, epoch.getEpochNo(),
                        epoch.getMissingBindingCursorScopeKey(), LIMIT + 1
                ), "bounded missing bindings"
        ));
        boolean hasMore = page.size() > LIMIT;
        List<DataPullScopeBindingEpoch> rows = first(page);
        List<ScheduleBindingCloseCommand> closes = new ArrayList<>(rows.size());
        for (DataPullScopeBindingEpoch binding : rows) {
            binding.validate();
            if (binding.getOperationCode() != operation
                    || !now.isAfter(binding.getEffectiveFromUtc())) {
                throw new IllegalStateException("missing binding close window is invalid");
            }
            closes.add(new ScheduleBindingCloseCommand(
                    binding.getBindingId(), binding.getPayloadSha256(), now
            ));
        }
        closeBatch(operation, closes);
        String cursor = rows.isEmpty() ? epoch.getMissingBindingCursorScopeKey()
                : rows.get(rows.size() - 1).getScopeKey();
        requireOne(apply.advanceBindingMissingPhase(
                operation, epoch.getEpochNo(), epoch.getVersion(),
                epoch.getMissingBindingCursorScopeKey(), cursor,
                hasMore ? "BINDING_MISSING" : "SCHEDULING"
        ), "binding-missing phase CAS");
    }

    private void reconcilePresent(
            OperationCode operation,
            List<ScheduleSourceStageRow> rows,
            LocalDateTime now
    ) {
        List<String> keys = scopeKeys(rows);
        Map<String, DataPullScopeBindingEpoch> latest = byScope(
                bindings.lockLatestBindingsByScopeKeys(operation, keys)
        );
        List<DataPullScopeBindingEpoch> inserts = new ArrayList<>();
        List<ScheduleBindingCloseCommand> closes = new ArrayList<>();
        Map<String, DataPullScopeBindingCandidate> expected = new HashMap<>();
        Map<String, String> expectedInsertedIds = new HashMap<>();
        for (ScheduleSourceStageRow row : rows) {
            DataPullScopeBindingCandidate candidate = Objects.requireNonNull(
                    row.toBinding(), "staged DP08 binding"
            );
            if (candidate.getEffectiveFromUtc().isAfter(now)) {
                throw new IllegalStateException("DP_SCOPE_BINDING_FUTURE_EFFECTIVE");
            }
            DataPullScopeBindingEpoch previous = latest.get(row.getScopeKey());
            DataPullScopeBindingCandidate transition = candidate;
            boolean previousOpen = previous != null && previous.getEffectiveUntilUtc() == null;
            if (previous != null && (!previousOpen || !previous.samePayload(candidate))) {
                LocalDateTime effective = nextEffectiveFrom(previous, candidate, now);
                transition = new DataPullScopeBindingCandidate(
                        operation, candidate.getScopeKey(), candidate.getPayloadType(),
                        candidate.getPayload(), effective
                );
                if (previousOpen) {
                    closes.add(new ScheduleBindingCloseCommand(
                            previous.getBindingId(), previous.getPayloadSha256(), effective
                    ));
                }
            }
            if (previous == null || !previousOpen || !previous.samePayload(candidate)) {
                inserts.add(DataPullScopeBindingEpoch.open(transition, now));
                expectedInsertedIds.put(row.getScopeKey(), transition.getBindingId());
            }
            expected.put(row.getScopeKey(), transition);
        }
        closeBatch(operation, closes);
        if (!inserts.isEmpty()) bindings.insertOpenBindings(inserts);
        Map<String, DataPullScopeBindingEpoch> stored = byScope(
                bindings.lockOpenBindingsByScopeKeys(operation, keys)
        );
        for (Map.Entry<String, DataPullScopeBindingCandidate> item : expected.entrySet()) {
            DataPullScopeBindingEpoch binding = stored.get(item.getKey());
            if (binding == null || !binding.samePayload(item.getValue())
                    || binding.getEffectiveUntilUtc() != null
                    || (expectedInsertedIds.containsKey(item.getKey())
                        && !expectedInsertedIds.get(item.getKey())
                                .equals(binding.getBindingId()))) {
                throw new IllegalStateException("DP_SCOPE_BINDING_BATCH_DRIFT");
            }
        }
        if (apply.completeBindingStage(
                operation, rows.get(0).getEpochNo(), keys
        ) != rows.size()) {
            throw new IllegalStateException("binding stage batch changed an invalid row count");
        }
    }

    private LocalDateTime lockOperationNow(OperationCode operation) {
        if (bindings.lockActiveOperation(operation) == null) {
            throw new IllegalStateException("DP_SCOPE_BINDING_CUTOVER_INACTIVE");
        }
        return Objects.requireNonNull(bindings.selectDatabaseNowUtc(), "databaseNowUtc");
    }

    private static LocalDateTime nextEffectiveFrom(
            DataPullScopeBindingEpoch previous,
            DataPullScopeBindingCandidate candidate,
            LocalDateTime now
    ) {
        LocalDateTime effective = candidate.getEffectiveFromUtc();
        LocalDateTime priorBoundary = previous.getEffectiveUntilUtc() == null
                ? previous.getEffectiveFromUtc() : previous.getEffectiveUntilUtc();
        if (effective.isBefore(priorBoundary)) effective = priorBoundary;
        if (!effective.isAfter(previous.getEffectiveFromUtc())) effective = now;
        if (effective.isBefore(priorBoundary) || !effective.isAfter(previous.getEffectiveFromUtc())) {
            throw new IllegalStateException("DP_SCOPE_BINDING_NON_MONOTONIC");
        }
        return effective;
    }

    private void closeBatch(OperationCode operation, List<ScheduleBindingCloseCommand> values) {
        if (!values.isEmpty() && bindings.closeBindings(operation, values) != values.size()) {
            throw new IllegalStateException("scope binding close batch lost its CAS");
        }
    }

    private ScheduleSourceEpochRow requireEpoch(OperationCode operation, String state) {
        ScheduleSourceEpochRow epoch = scans.lockActiveEpoch(operation);
        if (epoch == null || !state.equals(epoch.getEpochState())) {
            throw new IllegalStateException("schedule epoch is in the wrong binding phase");
        }
        return epoch;
    }

    private static Map<String, DataPullScopeBindingEpoch> byScope(
            List<DataPullScopeBindingEpoch> values
    ) {
        Map<String, DataPullScopeBindingEpoch> result = new HashMap<>();
        for (DataPullScopeBindingEpoch value : List.copyOf(values)) {
            value.validate();
            if (result.put(value.getScopeKey(), value) != null) {
                throw new IllegalStateException("duplicate open schedule binding");
            }
        }
        return result;
    }

    private static List<String> scopeKeys(List<ScheduleSourceStageRow> rows) {
        List<String> result = new ArrayList<>(rows.size());
        for (ScheduleSourceStageRow row : rows) result.add(row.getScopeKey());
        return result;
    }

    private static <T> List<T> first(List<T> values) {
        List<T> result = new ArrayList<>(Math.min(values.size(), LIMIT));
        for (int index = 0; index < values.size() && index < LIMIT; index++) {
            result.add(Objects.requireNonNull(values.get(index), "batch item"));
        }
        return result;
    }

    private static void requireDp08(OperationCode operation) {
        if (operation != OperationCode.DP08A && operation != OperationCode.DP08B) {
            throw new IllegalArgumentException("binding phase is DP08-only");
        }
    }

    private static void requireOne(int changed, String action) {
        if (changed != 1) throw new IllegalStateException(action + " must affect one row");
    }
}
