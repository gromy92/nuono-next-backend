package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.ScheduleBatchEngine;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Advances one persisted schedule phase for one operation; the caller owns the transaction. */
public final class BoundedScheduleBatchEngine implements ScheduleBatchEngine {
    private final MyBatisScheduleRotationStore rotation;
    private final MyBatisScheduleManifestVerifier manifests;
    private final MyBatisScheduleReconciliationStore reconciliation;
    private final DataPullScheduleScanMapper scans;
    private final MyBatisScheduleSealedEpochApplier admissions;
    private final MyBatisScheduleBindingBatchApplier bindings;
    private final MyBatisScheduleTaskBatchApplier tasks;

    public BoundedScheduleBatchEngine(
            MyBatisScheduleRotationStore rotation,
            MyBatisScheduleManifestVerifier manifests,
            MyBatisScheduleReconciliationStore reconciliation,
            DataPullScheduleScanMapper scans,
            MyBatisScheduleSealedEpochApplier admissions,
            MyBatisScheduleBindingBatchApplier bindings,
            MyBatisScheduleTaskBatchApplier tasks
    ) {
        this.rotation = Objects.requireNonNull(rotation, "rotation");
        this.manifests = Objects.requireNonNull(manifests, "manifests");
        this.reconciliation = Objects.requireNonNull(reconciliation, "reconciliation");
        this.scans = Objects.requireNonNull(scans, "scans");
        this.admissions = Objects.requireNonNull(admissions, "admissions");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
    }

    @Override
    public List<OperationCode> reserveOperations(List<OperationCode> available) {
        return rotation.reserve(available);
    }

    @Override
    public ScheduleBatchEngine.Advance advance(
            DataPullJob job,
            DataPullSchedule schedule,
            Instant observedAt
    ) {
        DataPullJob owner = Objects.requireNonNull(job, "job");
        OperationCode operation = owner.operationCode();
        if (Objects.requireNonNull(schedule, "schedule").operationCode() != operation) {
            throw new IllegalArgumentException("schedule and job operation differ");
        }
        Instant upperBound = Objects.requireNonNull(observedAt, "observedAt");
        MyBatisScheduleManifestVerifier.Advance manifest = manifests.advance(operation);
        switch (manifest.getProgress()) {
            case REJECTED:
                return ScheduleBatchEngine.Advance.failed();
            case VERIFYING:
            case SEALED_NOW:
                return ScheduleBatchEngine.Advance.succeeded(List.of());
            case SEALED:
                break;
            default:
                throw new IllegalStateException("unsupported schedule manifest state");
        }
        String cutoverKey = manifest.requireSealedCutoverKey();
        ScheduleSourceEpochRow epoch = scans.lockActiveEpoch(operation);
        if (epoch == null || "PASS_ONE".equals(epoch.getEpochState())
                || "PASS_TWO".equals(epoch.getEpochState())
                || !cutoverKey.equals(epoch.getCutoverKey())) {
            reconciliation.advanceSource(operation, cutoverKey, upperBound);
            return ScheduleBatchEngine.Advance.succeeded(List.of());
        }
        switch (epoch.getEpochState()) {
            case "SEALED":
            case "ADMITTING":
                admissions.advanceAdmission(operation);
                return ScheduleBatchEngine.Advance.succeeded(List.of());
            case "BINDING_PRESENT":
                bindings.advancePresent(operation);
                return ScheduleBatchEngine.Advance.succeeded(List.of());
            case "BINDING_MISSING":
                bindings.advanceMissing(operation);
                return ScheduleBatchEngine.Advance.succeeded(List.of());
            case "SCHEDULING":
                return ScheduleBatchEngine.Advance.succeeded(
                        tasks.advance(owner, schedule, upperBound)
                );
            default:
                throw new IllegalStateException(
                        "active schedule epoch has unsupported phase:" + epoch.getEpochState()
                );
        }
    }

}
