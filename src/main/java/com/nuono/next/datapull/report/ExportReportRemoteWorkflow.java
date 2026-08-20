package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ContractFailurePolicy;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import java.util.Objects;

/** Owns one report export's create, readback, poll and download lifecycle. */
final class ExportReportRemoteWorkflow {
    private final ExportReportProvider provider;
    private final ReportCreateAttemptFence createAttemptFence;
    private final ReportTaskTransitions transitions;

    ExportReportRemoteWorkflow(
            ExportReportProvider provider,
            ReportCreateAttemptFence createAttemptFence,
            ReportTaskTransitions transitions
    ) {
        this.provider = provider;
        this.createAttemptFence = createAttemptFence;
        this.transitions = transitions;
    }

    AdvanceResult create(
            ExecutionContext context,
            ExportReportIntent intent,
            ExportReportCheckpoint checkpoint
    ) {
        ExportReportCheckpoint reconcile = checkpoint.preserveAttemptAt(
                ExportReportCheckpoint.Phase.RECONCILE_CREATE
        ).unknownCreateOutcome();
        final boolean prepared;
        try {
            prepared = createAttemptFence.prepareReadbackBeforeCreate(
                    context.getTask(), reconcile, context.getNowUtc()
            );
        } catch (RuntimeException localPrepareFailure) {
            return transitions.waitingReconcile(reconcile, "CREATE_INTENT_PREPARE_UNKNOWN");
        }
        if (!prepared) return transitions.waitingReconcile(reconcile, "CREATE_INTENT_STALE_FENCE");
        ProviderOutcome<RemoteExportHandle> outcome;
        try {
            outcome = Objects.requireNonNull(provider.create(intent), "create outcome");
        } catch (RuntimeException ambiguousCreateFailure) {
            return transitions.waitingReconcile(
                    reconcile.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                    "CREATE_OUTCOME_UNKNOWN"
            );
        }
        if (outcome.getType() == ProviderOutcomeType.SUCCESS) {
            RemoteExportHandle handle = outcome.getValue();
            return handle == null
                    ? transitions.waitingReconcile(
                            reconcile.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                            "CREATE_SUCCESS_WITHOUT_HANDLE")
                    : transitions.waitingPoll(
                            checkpoint.progressTo(ExportReportCheckpoint.Phase.POLL), handle.getValue(),
                            "EXPORT_CREATED");
        }
        if (outcome.getType() == ProviderOutcomeType.UNKNOWN_OUTCOME
                || outcome.getType() == ProviderOutcomeType.CONTRACT_ERROR) {
            return transitions.waitingReconcile(
                    reconcile.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                    outcome.getSanitizedCode()
            );
        }
        ContractFailurePolicy.Decision decision = ContractFailurePolicy.decide(
                outcome, ContractFailurePolicy.NotFoundHandling.RETRY_SAME_RESOURCE
        );
        if (decision == ContractFailurePolicy.Decision.RETRY_WITH_BACKOFF) {
            return transitions.retry(context, outcome, reconcile.preserveAttemptAt(
                    ExportReportCheckpoint.Phase.CREATE
            ).retryAt(ExportReportCheckpoint.Phase.CREATE), null);
        }
        if (decision == ContractFailurePolicy.Decision.WAIT_AUTH) {
            return transitions.waitingAuth(reconcile.preserveAttemptAt(
                    ExportReportCheckpoint.Phase.CREATE
            ).retryAt(ExportReportCheckpoint.Phase.CREATE), null, outcome.getSanitizedCode());
        }
        if (decision == ContractFailurePolicy.Decision.WAIT_RECONCILE
                || decision == ContractFailurePolicy.Decision.RETRY_SAME_RESOURCE) {
            return transitions.waitingReconcile(
                    reconcile.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                    outcome.getSanitizedCode());
        }
        return transitions.failure(reconcile, null, outcome.getSanitizedCode());
    }

    AdvanceResult reconcile(
            ExecutionContext context,
            ExportReportIntent intent,
            ExportReportCheckpoint checkpoint
    ) {
        ProviderOutcome<ExportCreateReadback> outcome = transitions.readOutcome(
                () -> provider.findByRequestKey(intent)
        );
        if (outcome.getType() == ProviderOutcomeType.SUCCESS) {
            ExportCreateReadback readback = outcome.getValue();
            if (readback == null || !readback.proves(intent)) {
                return transitions.retryUntyped(context, checkpoint.preserveAttemptAt(
                        ExportReportCheckpoint.Phase.RECONCILE_CREATE
                ), null, "LOOKUP_AUTHORITY_INVALID");
            }
            if (readback.getStatus() == ExportCreateReadback.Status.FOUND) {
                return transitions.waitingPoll(
                        checkpoint.progressTo(ExportReportCheckpoint.Phase.POLL),
                        readback.getHandle().getValue(), "EXPORT_RECONCILED");
            }
            if (readback.getStatus() == ExportCreateReadback.Status.TERMINALLY_ABSENT) {
                return transitions.waitingReconcile(
                        checkpoint.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                        "CREATE_ABSENCE_PROOF_UNBOUND_FAIL_CLOSED");
            }
            return transitions.retryUntyped(context, checkpoint, null, "LOOKUP_STATUS_INVALID");
        }
        if (outcome.getType() == ProviderOutcomeType.NOT_FOUND) {
            return checkpoint.isCreateOutcomeUnknown()
                    ? transitions.waitingReconcile(
                            checkpoint.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                            "CREATE_UNKNOWN_NOT_FOUND_FAIL_CLOSED")
                    : transitions.queued(
                            checkpoint.preserveAttemptAt(ExportReportCheckpoint.Phase.CREATE), null);
        }
        if (checkpoint.isCreateOutcomeUnknown()
                && outcome.getType() == ProviderOutcomeType.CONTRACT_ERROR) {
            return provider.retryUnknownCreateAfterReadbackFailure()
                    ? transitions.waitingRecreate(checkpoint, "READ_ONLY_EXPORT_RETRY_AFTER_RECONCILE")
                    : transitions.waitingReconcile(
                            checkpoint.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                            outcome.getSanitizedCode());
        }
        return transitions.handleReadFailure(context, outcome, checkpoint, null,
                "LOOKUP_OUTCOME_INVALID");
    }

    AdvanceResult poll(
            ExecutionContext context,
            ExportReportIntent intent,
            ExportReportCheckpoint checkpoint
    ) {
        RemoteExportHandle handle = transitions.taskHandle(context.getTask());
        if (handle == null) return transitions.waitingReconcile(
                checkpoint.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                "POLL_HANDLE_MISSING");
        ProviderOutcome<ExportPollResult> outcome = transitions.readOutcome(
                () -> provider.poll(intent, handle)
        );
        if (outcome.getType() != ProviderOutcomeType.SUCCESS) {
            return transitions.handleReadFailure(context, outcome, checkpoint, handle.getValue(),
                    "POLL_OUTCOME_INVALID");
        }
        ExportPollResult result = outcome.getValue();
        if (result == null) return transitions.retryUntyped(context, checkpoint, handle.getValue(),
                "POLL_SUCCESS_WITHOUT_STATUS");
        switch (result.getStatus()) {
            case PENDING: return transitions.waitingPoll(
                    checkpoint.progressTo(ExportReportCheckpoint.Phase.POLL), handle.getValue(),
                    result.getSanitizedCode());
            case READY:
                return result.provesReadyFor(intent, handle)
                        ? transitions.queued(checkpoint.download(result.getDownloadLocatorReference(),
                                result.getArtifactAuthority()), handle.getValue())
                        : transitions.retryUntyped(context, checkpoint, handle.getValue(),
                                "POLL_ARTIFACT_AUTHORITY_INVALID");
            case EMPTY:
                return result.provesAuthoritativeEmptyFor(intent, handle)
                        ? AdvanceResult.succeeded()
                        : transitions.retryUntyped(context, checkpoint, handle.getValue(),
                                "POLL_EMPTY_PROOF_INVALID");
            case TERMINAL_FAILURE: return transitions.waitingRecreate(checkpoint, result.getSanitizedCode());
            default: return transitions.retryUntyped(context, checkpoint, handle.getValue(),
                    "POLL_STATUS_UNSUPPORTED");
        }
    }

    AdvanceResult download(
            ExecutionContext context,
            ExportReportIntent intent,
            ExportReportCheckpoint checkpoint
    ) {
        RemoteExportHandle handle = transitions.taskHandle(context.getTask());
        if (handle == null) return transitions.waitingReconcile(
                checkpoint.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                "DOWNLOAD_HANDLE_MISSING");
        String locator = checkpoint.getDownloadLocatorReference();
        ReportArtifactAuthority authority = checkpoint.getArtifactAuthority();
        if (locator == null || authority == null || !authority.proves(intent, handle)) {
            return transitions.waitingPoll(checkpoint.retryAt(ExportReportCheckpoint.Phase.POLL),
                    handle.getValue(), "DOWNLOAD_AUTHORITY_INVALID");
        }
        ProviderOutcome<DownloadedReportArtifact> outcome = transitions.readOutcome(
                () -> provider.download(intent, handle, locator)
        );
        if (outcome.getType() == ProviderOutcomeType.NOT_FOUND) {
            return transitions.waitingPoll(checkpoint.progressTo(ExportReportCheckpoint.Phase.POLL),
                    handle.getValue(), outcome.getSanitizedCode());
        }
        if (outcome.getType() != ProviderOutcomeType.SUCCESS) {
            return transitions.handleReadFailure(context, outcome, checkpoint, handle.getValue(),
                    "DOWNLOAD_OUTCOME_INVALID");
        }
        DownloadedReportArtifact artifact = outcome.getValue();
        return artifact == null
                ? transitions.retryUntyped(context, checkpoint, handle.getValue(),
                        "DOWNLOAD_SUCCESS_WITHOUT_ARTIFACT")
                : transitions.queued(checkpoint.apply(artifact), handle.getValue());
    }
}
