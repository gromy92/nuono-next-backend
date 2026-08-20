package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Report Job Module: validates one claimed task and dispatches its durable report phase. */
public final class ExportReportJob implements DataPullJob {
    private static final String INITIAL_STEP = "REPORT_PREPARE_INTENT";
    private static final Set<OperationCode> SUPPORTED = EnumSet.of(
            OperationCode.DP01, OperationCode.DP02, OperationCode.DP03, OperationCode.DP07B
    );

    private final OperationCode operationCode;
    private final String providerChannel;
    private final ExportReportScopeSource scopeSource;
    private final ExportReportCheckpointCodec checkpointCodec = new ExportReportCheckpointCodec();
    private final ExportReportRemoteWorkflow remoteWorkflow;
    private final ExportReportImporter importer;
    private final ReportTaskTransitions transitions;

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
        this.importer = Objects.requireNonNull(importer, "importer");
        this.transitions = new ReportTaskTransitions(
                this.operationCode,
                checkpointCodec,
                Objects.requireNonNull(providerWaitTransition, "providerWaitTransition"),
                requirePositive(pollDelay, "pollDelay"),
                requirePositive(reconcileDelay, "reconcileDelay")
        );
        this.remoteWorkflow = new ExportReportRemoteWorkflow(
                Objects.requireNonNull(provider, "provider"),
                Objects.requireNonNull(createAttemptFence, "createAttemptFence"),
                transitions
        );
    }

    @Override public OperationCode operationCode() { return operationCode; }
    @Override public String providerChannel() { return providerChannel; }
    @Override public String initialStep() { return INITIAL_STEP; }

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
        } catch (RuntimeException invalidTask) {
            return AdvanceResult.failed(task.getStepCode(), task.getRemoteHandle(), task.getCheckpoint(),
                    "REPORT_TASK_CONTEXT_INVALID");
        }
        if (task.getOperationCode() != operationCode || !providerChannel.equals(task.getProviderChannel())) {
            return AdvanceResult.failed(task.getCheckpoint(), "REPORT_JOB_CONTEXT_MISMATCH");
        }
        ExportReportCheckpoint checkpoint;
        try {
            checkpoint = task.getCheckpoint() == null ? initialCheckpoint(task, intent)
                    : checkpointCodec.decode(task.getCheckpoint());
            if (!intent.getStableRequestKey().equals(checkpoint.getStableRequestKey())) {
                return transitions.failure(checkpoint, task.getRemoteHandle(), "REPORT_INTENT_DRIFT");
            }
        } catch (RuntimeException invalidCheckpoint) {
            return AdvanceResult.failed(task.getCheckpoint(), "REPORT_CHECKPOINT_INVALID");
        }
        if (task.getCheckpoint() == null) return transitions.queued(checkpoint, task.getRemoteHandle());
        switch (checkpoint.getPhase()) {
            case CREATE: return remoteWorkflow.create(context, intent, checkpoint);
            case RECONCILE_CREATE: return remoteWorkflow.reconcile(context, intent, checkpoint);
            case POLL: return remoteWorkflow.poll(context, intent, checkpoint);
            case DOWNLOAD: return remoteWorkflow.download(context, intent, checkpoint);
            case APPLY: return apply(context, intent, checkpoint, task.getRemoteHandle());
            default: return transitions.failure(checkpoint, task.getRemoteHandle(),
                    "REPORT_PHASE_UNSUPPORTED");
        }
    }

    private AdvanceResult apply(
            ExecutionContext context,
            ExportReportIntent intent,
            ExportReportCheckpoint checkpoint,
            String remoteHandle
    ) {
        ReportImportResult result;
        try {
            result = Objects.requireNonNull(importer.importComplete(intent, checkpoint.getArtifact()),
                    "import result");
        } catch (RuntimeException unknownLocalResult) {
            return transitions.retryUntyped(context, checkpoint, remoteHandle,
                    "REPORT_IMPORT_UNKNOWN_RESULT");
        }
        switch (result.getStatus()) {
            case APPLIED: return AdvanceResult.succeeded();
            case IN_PROGRESS: return transitions.queued(
                    checkpoint.preserveAttemptAt(ExportReportCheckpoint.Phase.APPLY), remoteHandle);
            case AWAITING_AUTHORITATIVE_EMPTY_PROOF: return transitions.waitingPoll(
                    checkpoint.progressTo(ExportReportCheckpoint.Phase.POLL), remoteHandle,
                    result.getSanitizedCode());
            case STALE_FENCE: return transitions.queued(checkpoint, remoteHandle);
            case CONTRACT_ERROR: return transitions.retry(context,
                    ProviderOutcome.contractError(result.getSanitizedCode()),
                    checkpoint.retryAt(ExportReportCheckpoint.Phase.APPLY), remoteHandle);
            default: return transitions.retryUntyped(context,
                    checkpoint.preserveAttemptAt(ExportReportCheckpoint.Phase.APPLY), remoteHandle,
                    result.getSanitizedCode());
        }
    }

    private ExportReportCheckpoint initialCheckpoint(DataPullTask task, ExportReportIntent intent) {
        ExportReportCheckpoint.Phase phase = task.getRemoteHandle() == null
                ? ExportReportCheckpoint.Phase.CREATE : ExportReportCheckpoint.Phase.POLL;
        if (task.getRemoteHandle() != null) new RemoteExportHandle(task.getRemoteHandle());
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
