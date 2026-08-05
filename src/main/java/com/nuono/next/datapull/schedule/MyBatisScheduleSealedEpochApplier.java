package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleAnchorMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleApplyMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeAdmissionMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Consumes a sealed epoch in fixed-call phases; incomplete source scans never enter here. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class MyBatisScheduleSealedEpochApplier {
    private static final int LIMIT = MyBatisScheduleReconciliationStore.SCOPES_PER_STEP;

    private final DataPullScheduleScanMapper scans;
    private final DataPullScheduleApplyMapper apply;
    private final DataPullScopeAdmissionMapper admissions;
    private final DataPullScheduleAnchorMapper anchors;

    public MyBatisScheduleSealedEpochApplier(
            DataPullScheduleScanMapper scans,
            DataPullScheduleApplyMapper apply,
            DataPullScopeAdmissionMapper admissions,
            DataPullScheduleAnchorMapper anchors
    ) {
        this.scans = Objects.requireNonNull(scans, "scans");
        this.apply = Objects.requireNonNull(apply, "apply");
        this.admissions = Objects.requireNonNull(admissions, "admissions");
        this.anchors = Objects.requireNonNull(anchors, "anchors");
    }

    public Progress advanceAdmission(OperationCode operation) {
        ScheduleSourceEpochRow epoch = requireEpoch(operation, "SEALED", "ADMITTING");
        List<ScheduleSourceStageRow> page = List.copyOf(Objects.requireNonNull(
                apply.listAdmissionStageAfter(
                        operation, epoch.getEpochNo(), epoch.getAdmissionCursorScopeKey(), LIMIT + 1
                ), "bounded admission stage"
        ));
        boolean hasMore = page.size() > LIMIT;
        List<ScheduleSourceStageRow> rows = first(page, LIMIT);
        DataPullScheduleCutover cutover = admissions.lockActiveCutover(operation);
        if (cutover == null) throw new IllegalStateException("DP_SCHEDULE_CUTOVER_NOT_ACTIVE");
        cutover.validateActive();
        if (!cutover.getCutoverKey().equals(epoch.getCutoverKey())) {
            throw new IllegalStateException("DP_SCHEDULE_EPOCH_CUTOVER_DRIFT");
        }
        LocalDateTime observedAt = Objects.requireNonNull(
                admissions.selectDatabaseNowUtc(), "databaseNowUtc"
        );
        if (observedAt.isBefore(cutover.getActivatedAtUtc())) {
            throw new IllegalStateException("schedule admission predates active cutover");
        }
        if (!rows.isEmpty()) {
            apply.insertPostCutoverAdmissions(
                    operation, cutover.getCutoverKey(), observedAt, rows
            );
            reconcileAnchors(operation, cutover, rows);
        }
        String cursor = rows.isEmpty() ? epoch.getAdmissionCursorScopeKey()
                : rows.get(rows.size() - 1).getScopeKey();
        String next = hasMore ? "ADMITTING"
                : (isDp08(operation) ? "BINDING_PRESENT" : "SCHEDULING");
        requireOne(apply.advanceAdmissionPhase(
                operation, epoch.getEpochNo(), epoch.getVersion(),
                epoch.getAdmissionCursorScopeKey(), cursor, next
        ), "admission phase CAS");
        return hasMore ? Progress.IN_PROGRESS : Progress.COMPLETE;
    }

    private void reconcileAnchors(
            OperationCode operation,
            DataPullScheduleCutover cutover,
            List<ScheduleSourceStageRow> rows
    ) {
        List<String> keys = scopeKeys(rows);
        Map<String, DataPullScopeAdmission> admitted = admissionsByScope(
                admissions.listByScopeKeys(keys)
        );
        List<DataPullScheduleAnchor> existing = anchors.listActiveAnchorsByScopeKeys(
                operation, keys
        );
        Map<String, DataPullScheduleAnchor> anchored = anchorsByScope(existing);
        List<DataPullScheduleAnchor> inserts = new ArrayList<>();
        for (ScheduleSourceStageRow row : rows) {
            DataPullScopeAdmission admission = admitted.get(row.getScopeKey());
            if (admission == null) throw new IllegalStateException("schedule admission missing");
            new AdmittedDataPullScope(row.toScope(), admission);
            if (!cutover.getCutoverKey().equals(admission.getCutoverKey())) {
                throw new IllegalStateException("schedule admission cutover drift");
            }
            if (anchored.containsKey(row.getScopeKey())) continue;
            if (admission.getAdmissionKind() == DataPullScopeAdmission.Kind.CUTOVER_EXISTING) {
                throw new IllegalStateException("DP_SCHEDULE_CUTOVER_SCOPE_ANCHOR_OMITTED");
            }
            inserts.add(DataPullScheduleAnchor.postCutoverScope(
                    operation, admission,
                    DataPullScheduleAnchorEvidence.postCutoverReconcileAfter(
                            admission.getFirstEligibleAtUtc()
                    ), admission.getAdmittedAtUtc()
            ));
        }
        if (!inserts.isEmpty()) apply.insertPostCutoverAnchors(inserts);
        anchored = anchorsByScope(anchors.listActiveAnchorsByScopeKeys(operation, keys));
        List<ScheduleAnchorStageUpdate> updates = new ArrayList<>(rows.size());
        for (ScheduleSourceStageRow row : rows) {
            DataPullScopeAdmission admission = admitted.get(row.getScopeKey());
            DataPullScheduleAnchor anchor = anchored.get(row.getScopeKey());
            if (anchor == null) throw new IllegalStateException("schedule anchor missing");
            requireAnchor(operation, cutover, admission, anchor);
            updates.add(new ScheduleAnchorStageUpdate(
                    row.getScopeKey(), anchor.getReconcileAfterUtc()
            ));
        }
        if (apply.completeAdmissionStage(
                operation, rows.get(0).getEpochNo(), updates
        ) != updates.size()) {
            throw new IllegalStateException("admission stage batch changed an invalid row count");
        }
    }

    private static void requireAnchor(
            OperationCode operation,
            DataPullScheduleCutover cutover,
            DataPullScopeAdmission admission,
            DataPullScheduleAnchor anchor
    ) {
        anchor.validate();
        if (anchor.getOperationCode() != operation
                || !cutover.getCutoverKey().equals(anchor.getCutoverKey())
                || !admission.getScopeKey().equals(anchor.getScopeKey())
                || admission.getAdmissionKind() != anchor.getAdmissionKind()
                || !Objects.equals(admission.getFirstEligibleAtUtc(), anchor.getFirstEligibleAtUtc())
                || !admission.getSourceBindingSha256().equals(anchor.getSourceBindingSha256())) {
            throw new IllegalStateException("schedule anchor admission evidence drift");
        }
    }

    private ScheduleSourceEpochRow requireEpoch(OperationCode operation, String... states) {
        ScheduleSourceEpochRow epoch = scans.lockActiveEpoch(operation);
        if (epoch == null) throw new IllegalStateException("active schedule epoch is missing");
        for (String state : states) if (state.equals(epoch.getEpochState())) return epoch;
        throw new IllegalStateException("schedule epoch is in the wrong apply phase");
    }

    private static Map<String, DataPullScopeAdmission> admissionsByScope(
            List<DataPullScopeAdmission> values
    ) {
        Map<String, DataPullScopeAdmission> result = new HashMap<>();
        for (DataPullScopeAdmission value : List.copyOf(values)) {
            value.validate();
            if (result.put(value.getScopeKey(), value) != null) {
                throw new IllegalStateException("duplicate schedule admission");
            }
        }
        return result;
    }

    private static Map<String, DataPullScheduleAnchor> anchorsByScope(
            List<DataPullScheduleAnchor> values
    ) {
        Map<String, DataPullScheduleAnchor> result = new HashMap<>();
        for (DataPullScheduleAnchor value : List.copyOf(values)) {
            value.validate();
            if (result.put(value.getScopeKey(), value) != null) {
                throw new IllegalStateException("duplicate schedule anchor");
            }
        }
        return result;
    }

    private static List<String> scopeKeys(List<ScheduleSourceStageRow> rows) {
        List<String> result = new ArrayList<>(rows.size());
        for (ScheduleSourceStageRow row : rows) result.add(row.getScopeKey());
        return result;
    }

    private static <T> List<T> first(List<T> values, int limit) {
        List<T> result = new ArrayList<>(Math.min(values.size(), limit));
        for (int index = 0; index < values.size() && index < limit; index++) {
            result.add(Objects.requireNonNull(values.get(index), "batch item"));
        }
        return result;
    }

    private static boolean isDp08(OperationCode operation) {
        return operation == OperationCode.DP08A || operation == OperationCode.DP08B;
    }

    private static void requireOne(int changed, String action) {
        if (changed != 1) throw new IllegalStateException(action + " must affect one row");
    }

    public enum Progress { IN_PROGRESS, COMPLETE }
}
