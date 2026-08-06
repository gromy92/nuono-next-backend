package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ContractFailurePolicy;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import java.time.Duration;

final class ExportReportTransitions {

    private final OperationCode operationCode;
    private final ExportReportCheckpointCodec checkpointCodec;
    private final ProviderWaitTransition providerWaitTransition;
    private final Duration pollDelay;
    private final Duration reconcileDelay;

    ExportReportTransitions(
            OperationCode operationCode,
            ExportReportCheckpointCodec checkpointCodec,
            ProviderWaitTransition providerWaitTransition,
            Duration pollDelay,
            Duration reconcileDelay
    ) {
        this.operationCode = operationCode;
        this.checkpointCodec = checkpointCodec;
        this.providerWaitTransition = providerWaitTransition;
        this.pollDelay = pollDelay;
        this.reconcileDelay = reconcileDelay;
    }

    AdvanceResult handleReadFailure(
            ExecutionContext context,
            ProviderOutcome<?> outcome,
            ExportReportCheckpoint checkpoint,
            String remoteHandle,
            String invalidCode
    ) {
        ContractFailurePolicy.Decision decision = ContractFailurePolicy.decide(
                outcome,
                ContractFailurePolicy.NotFoundHandling.RETRY_SAME_RESOURCE
        );
        switch (decision) {
            case RETRY_WITH_BACKOFF:
                return retry(
                        context,
                        outcome,
                        checkpoint.retryAt(checkpoint.getPhase()),
                        remoteHandle
                );
            case WAIT_AUTH:
                return waitingAuth(
                        checkpoint.retryAt(checkpoint.getPhase()),
                        remoteHandle,
                        outcome.getSanitizedCode()
                );
            case WAIT_RECONCILE:
                return waitingRemote(
                        checkpoint,
                        remoteHandle,
                        reconcileDelay,
                        outcome.getSanitizedCode()
                );
            case FAIL_TASK:
                return failure(checkpoint, remoteHandle, outcome.getSanitizedCode());
            case RETRY_SAME_RESOURCE:
                return waitingRemote(
                        checkpoint.retryAt(checkpoint.getPhase()),
                        remoteHandle,
                        reconcileDelay,
                        outcome.getSanitizedCode()
                );
            default:
                return failure(checkpoint, remoteHandle, invalidCode);
        }
    }

    AdvanceResult retry(
            ExecutionContext context,
            ProviderOutcome<?> outcome,
            ExportReportCheckpoint checkpoint,
            String remoteHandle
    ) {
        return providerWaitTransition.waitFor(
                context.getTask(),
                operationCode,
                outcome,
                checkpoint.getConsecutiveRetryAttempt(),
                step(checkpoint),
                remoteHandle,
                checkpointCodec.encode(checkpoint), null
        );
    }

    AdvanceResult retryUntyped(
            ExecutionContext context,
            ExportReportCheckpoint checkpoint,
            String remoteHandle,
            String code
    ) {
        return retry(
                context,
                ProviderOutcome.transientFailure(code),
                checkpoint.retryAt(checkpoint.getPhase()),
                remoteHandle
        );
    }

    AdvanceResult waitingAuth(
            ExportReportCheckpoint checkpoint,
            String remoteHandle,
            String code
    ) {
        return providerWaitTransition.waitFor(
                null,
                operationCode,
                ProviderOutcome.authRequired(code),
                1,
                step(checkpoint),
                remoteHandle,
                checkpointCodec.encode(checkpoint), null
        );
    }

    AdvanceResult queued(ExportReportCheckpoint checkpoint, String remoteHandle) {
        return AdvanceResult.queued(
                step(checkpoint),
                remoteHandle,
                checkpointCodec.encode(checkpoint)
        );
    }

    AdvanceResult waitingPoll(ExportReportCheckpoint checkpoint, String remoteHandle, String code) {
        return waitingRemote(checkpoint, remoteHandle, pollDelay, code);
    }

    AdvanceResult waitingReconcile(
            ExportReportCheckpoint checkpoint,
            String code
    ) {
        return waitingRemote(checkpoint, null, reconcileDelay, code);
    }

    AdvanceResult waitingRecreate(
            ExportReportCheckpoint checkpoint,
            String code
    ) {
        return waitingRemote(
                checkpoint.retryAt(ExportReportCheckpoint.Phase.CREATE),
                null,
                reconcileDelay,
                code
        );
    }

    AdvanceResult failure(
            ExportReportCheckpoint checkpoint,
            String remoteHandle,
            String code
    ) {
        return AdvanceResult.failed(
                step(checkpoint),
                remoteHandle,
                checkpointCodec.encode(checkpoint),
                code
        );
    }

    RemoteExportHandle taskHandle(DataPullTask task) {
        try {
            return task.getRemoteHandle() == null ? null : new RemoteExportHandle(task.getRemoteHandle());
        } catch (RuntimeException invalidHandle) {
            return null;
        }
    }

    <T> ProviderOutcome<T> readOutcome(ProviderRead<T> call) {
        try {
            return java.util.Objects.requireNonNull(call.execute(), "provider outcome");
        } catch (RuntimeException untypedFailure) {
            return ProviderOutcome.transientFailure("REPORT_PROVIDER_UNTYPED_FAILURE");
        }
    }

    private AdvanceResult waitingRemote(
            ExportReportCheckpoint checkpoint,
            String remoteHandle,
            Duration delay,
            String code
    ) {
        return AdvanceResult.waitingRemote(
                step(checkpoint),
                remoteHandle,
                checkpointCodec.encode(checkpoint),
                delay,
                code
        );
    }

    private String step(ExportReportCheckpoint checkpoint) {
        return "REPORT_" + checkpoint.getPhase().name();
    }

    @FunctionalInterface
    interface ProviderRead<T> {
        ProviderOutcome<T> execute();
    }
}
