package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Deep export-report runtime Module shared by DP-01, DP-02, DP-03 and DP-07-B.
 *
 * <p>The public Interface is one bounded {@link #advance(ExecutionContext)}. Its Implementation
 * persists a deterministic create intent before the first remote call, never repeats an ambiguous
 * create before a read-only reconciliation, keeps one handle through polling and download, and
 * delegates complete-file validation plus fact writes to one DP-owned transaction.</p>
 */
public final class ExportReportJob implements DataPullJob {

    private static final String INITIAL_STEP = "REPORT_PREPARE_INTENT";
    private static final Set<OperationCode> SUPPORTED = EnumSet.of(
            OperationCode.DP01,
            OperationCode.DP02,
            OperationCode.DP03,
            OperationCode.DP07B
    );

    private final OperationCode operationCode;
    private final String providerChannel;
    private final ExportReportScopeSource scopeSource;
    private final ExportReportCheckpointCodec checkpointCodec;
    private final ExportReportRemoteSteps remoteSteps;
    private final ExportReportApplyStep applyStep;

    public ExportReportJob(
            OperationCode operationCode,
            String providerChannel,
            ExportReportScopeSource scopeSource,
            ExportReportProvider provider,
            ExportReportImporter importer,
            ReportCreateAttemptFence createAttemptFence,
            ProviderWaitTransition providerWaitTransition,
            Duration pollDelay,
            Duration reconcileDelay
    ) {
        this.operationCode = requireSupported(operationCode);
        this.providerChannel = ReportContract.requireIdentity(providerChannel, "providerChannel");
        this.scopeSource = Objects.requireNonNull(scopeSource, "scopeSource");
        this.checkpointCodec = new ExportReportCheckpointCodec();
        ExportReportTransitions transitions = new ExportReportTransitions(
                this.operationCode,
                checkpointCodec,
                Objects.requireNonNull(providerWaitTransition, "providerWaitTransition"),
                requirePositive(pollDelay, "pollDelay"),
                requirePositive(reconcileDelay, "reconcileDelay")
        );
        this.remoteSteps = new ExportReportRemoteSteps(
                Objects.requireNonNull(provider, "provider"),
                Objects.requireNonNull(createAttemptFence, "createAttemptFence"),
                transitions
        );
        this.applyStep = new ExportReportApplyStep(
                Objects.requireNonNull(importer, "importer"),
                transitions
        );
    }

    @Override
    public OperationCode operationCode() {
        return operationCode;
    }

    @Override
    public String providerChannel() {
        return providerChannel;
    }

    @Override
    public String initialStep() {
        return INITIAL_STEP;
    }

    @Override
    public List<DataPullScope> listScopes() {
        return List.copyOf(Objects.requireNonNull(scopeSource.listScopes(), "report scopes"));
    }

    @Override
    public AdvanceResult advance(ExecutionContext context) {
        DataPullTask task;
        try {
            task = Objects.requireNonNull(context, "context").getTask();
        } catch (RuntimeException invalidContext) {
            return AdvanceResult.failed(null, "REPORT_TASK_CONTEXT_INVALID");
        }
        ExportReportIntent intent;
        try {
            intent = ExportReportIntent.from(context);
        } catch (RuntimeException invalidTaskSnapshot) {
            return AdvanceResult.failed(
                    task.getStepCode(),
                    task.getRemoteHandle(),
                    task.getCheckpoint(),
                    "REPORT_TASK_CONTEXT_INVALID"
            );
        }
        if (task.getOperationCode() != operationCode
                || !providerChannel.equals(task.getProviderChannel())) {
            return AdvanceResult.failed(task.getCheckpoint(), "REPORT_JOB_CONTEXT_MISMATCH");
        }

        ExportReportCheckpoint checkpoint;
        try {
            checkpoint = task.getCheckpoint() == null
                    ? initialCheckpoint(task, intent)
                    : checkpointCodec.decode(task.getCheckpoint());
            if (!intent.getStableRequestKey().equals(checkpoint.getStableRequestKey())) {
                return remoteSteps.failure(checkpoint, task.getRemoteHandle(), "REPORT_INTENT_DRIFT");
            }
        } catch (RuntimeException invalidCheckpoint) {
            return AdvanceResult.failed(task.getCheckpoint(), "REPORT_CHECKPOINT_INVALID");
        }
        if (task.getCheckpoint() == null) {
            return remoteSteps.queued(checkpoint, task.getRemoteHandle());
        }

        switch (checkpoint.getPhase()) {
            case CREATE:
                return remoteSteps.create(context, intent, checkpoint);
            case RECONCILE_CREATE:
                return remoteSteps.reconcile(context, intent, checkpoint);
            case POLL:
                return remoteSteps.poll(context, intent, checkpoint);
            case DOWNLOAD:
                return remoteSteps.download(context, intent, checkpoint);
            case APPLY:
                return applyStep.apply(context, intent, checkpoint, task.getRemoteHandle());
            default:
                return remoteSteps.failure(
                        checkpoint,
                        task.getRemoteHandle(),
                        "REPORT_PHASE_UNSUPPORTED"
                );
        }
    }

    private ExportReportCheckpoint initialCheckpoint(
            DataPullTask task,
            ExportReportIntent intent
    ) {
        ExportReportCheckpoint.Phase phase = task.getRemoteHandle() == null
                ? ExportReportCheckpoint.Phase.CREATE
                : ExportReportCheckpoint.Phase.POLL;
        if (task.getRemoteHandle() != null) {
            new RemoteExportHandle(task.getRemoteHandle());
        }
        return ExportReportCheckpoint.at(phase, intent.getStableRequestKey());
    }

    private static OperationCode requireSupported(OperationCode operationCode) {
        OperationCode value = Objects.requireNonNull(operationCode, "operationCode");
        if (!SUPPORTED.contains(value)) {
            throw new IllegalArgumentException("only DP01, DP02, DP03 and DP07B are report jobs");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
