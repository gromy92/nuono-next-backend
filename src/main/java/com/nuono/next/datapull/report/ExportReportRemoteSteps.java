package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ContractFailurePolicy;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import java.util.Objects;

final class ExportReportRemoteSteps {
    private final ExportReportProvider provider;
    private final ReportCreateAttemptFence createAttemptFence;
    private final ExportReportTransitions transitions;
    private final ExportCreateReconciliationStep createReconciliation;

    ExportReportRemoteSteps(
            ExportReportProvider provider,
            ReportCreateAttemptFence createAttemptFence,
            ExportReportTransitions transitions
    ) {
        this.provider = provider;
        this.createAttemptFence = createAttemptFence;
        this.transitions = transitions;
        this.createReconciliation = new ExportCreateReconciliationStep(provider, transitions);
    }
    AdvanceResult create(ExecutionContext context, ExportReportIntent intent,
            ExportReportCheckpoint checkpoint) {
        ExportReportCheckpoint reconcileCheckpoint = checkpoint.preserveAttemptAt(
                ExportReportCheckpoint.Phase.RECONCILE_CREATE
        ).unknownCreateOutcome();
        final boolean prepared;
        try {
            prepared = createAttemptFence.prepareReadbackBeforeCreate(
                    context.getTask(),
                    reconcileCheckpoint,
                    context.getNowUtc()
            );
        } catch (RuntimeException localPrepareFailure) {
            return transitions.waitingReconcile(
                    reconcileCheckpoint,
                    "CREATE_INTENT_PREPARE_UNKNOWN"
            );
        }
        if (!prepared) {
            return transitions.waitingReconcile(
                    reconcileCheckpoint,
                    "CREATE_INTENT_STALE_FENCE"
            );
        }
        ProviderOutcome<RemoteExportHandle> outcome;
        try {
            outcome = Objects.requireNonNull(provider.create(intent), "create outcome");
        } catch (RuntimeException ambiguousCreateFailure) {
            return transitions.waitingReconcile(
                    reconcileCheckpoint.retryAt(
                            ExportReportCheckpoint.Phase.RECONCILE_CREATE
                    ),
                    "CREATE_OUTCOME_UNKNOWN"
            );
        }
        if (outcome.getType() == ProviderOutcomeType.SUCCESS) {
            RemoteExportHandle handle = outcome.getValue();
            if (handle == null) {
                // The provider may have accepted the create even though its response was
                // unusable. Reconcile the stable request key before any further create.
                return transitions.waitingReconcile(
                        reconcileCheckpoint.retryAt(
                                ExportReportCheckpoint.Phase.RECONCILE_CREATE
                        ),
                        "CREATE_SUCCESS_WITHOUT_HANDLE"
                );
            }
            return transitions.waitingPoll(
                    checkpoint.progressTo(ExportReportCheckpoint.Phase.POLL),
                    handle.getValue(),
                    "EXPORT_CREATED"
            );
        }
        if (outcome.getType() == ProviderOutcomeType.UNKNOWN_OUTCOME
                || outcome.getType() == ProviderOutcomeType.CONTRACT_ERROR) {
            return transitions.waitingReconcile(
                    reconcileCheckpoint.retryAt(
                            ExportReportCheckpoint.Phase.RECONCILE_CREATE
                    ),
                    outcome.getSanitizedCode()
            );
        }
        ContractFailurePolicy.Decision failure = ContractFailurePolicy.decide(
                outcome,
                ContractFailurePolicy.NotFoundHandling.RETRY_SAME_RESOURCE
        );
        if (failure == ContractFailurePolicy.Decision.RETRY_WITH_BACKOFF) {
            return transitions.retry(
                    context,
                    outcome,
                    reconcileCheckpoint.preserveAttemptAt(
                            ExportReportCheckpoint.Phase.CREATE
                    ).retryAt(
                            ExportReportCheckpoint.Phase.CREATE
                    ),
                    null
            );
        }
        if (failure == ContractFailurePolicy.Decision.WAIT_AUTH) {
            return transitions.waitingAuth(
                    reconcileCheckpoint.preserveAttemptAt(
                            ExportReportCheckpoint.Phase.CREATE
                    ).retryAt(
                            ExportReportCheckpoint.Phase.CREATE
                    ),
                    null,
                    outcome.getSanitizedCode()
            );
        }
        if (failure == ContractFailurePolicy.Decision.WAIT_RECONCILE) {
            return transitions.waitingReconcile(
                    reconcileCheckpoint.retryAt(
                            ExportReportCheckpoint.Phase.RECONCILE_CREATE
                    ),
                    outcome.getSanitizedCode()
            );
        }
        if (failure == ContractFailurePolicy.Decision.RETRY_SAME_RESOURCE) {
            return transitions.waitingReconcile(
                    reconcileCheckpoint.retryAt(
                            ExportReportCheckpoint.Phase.RECONCILE_CREATE
                    ),
                    outcome.getSanitizedCode()
            );
        }
        return transitions.failure(
                reconcileCheckpoint,
                null,
                outcome.getSanitizedCode()
        );
    }
    AdvanceResult reconcile(ExecutionContext context, ExportReportIntent intent,
            ExportReportCheckpoint checkpoint) {
        return createReconciliation.advance(context, intent, checkpoint);
    }
    AdvanceResult poll(ExecutionContext context, ExportReportIntent intent,
            ExportReportCheckpoint checkpoint) {
        RemoteExportHandle handle = transitions.taskHandle(context.getTask());
        if (handle == null) {
            return transitions.waitingReconcile(
                    checkpoint.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                    "POLL_HANDLE_MISSING"
            );
        }
        ProviderOutcome<ExportPollResult> outcome = transitions.readOutcome(
                () -> provider.poll(intent, handle)
        );
        if (outcome.getType() != ProviderOutcomeType.SUCCESS) {
            return transitions.handleReadFailure(
                    context,
                    outcome,
                    checkpoint,
                    handle.getValue(),
                    "POLL_OUTCOME_INVALID"
            );
        }
        ExportPollResult result = outcome.getValue();
        if (result == null) {
            return transitions.retryUntyped(
                    context,
                    checkpoint,
                    handle.getValue(),
                    "POLL_SUCCESS_WITHOUT_STATUS"
            );
        }
        switch (result.getStatus()) {
            case PENDING:
                return transitions.waitingPoll(
                        checkpoint.progressTo(ExportReportCheckpoint.Phase.POLL),
                        handle.getValue(),
                        result.getSanitizedCode()
                );
            case READY:
                if (!result.provesReadyFor(intent, handle)) {
                    return transitions.retryUntyped(
                            context,
                            checkpoint,
                            handle.getValue(),
                            "POLL_ARTIFACT_AUTHORITY_INVALID"
                    );
                }
                return queued(
                        checkpoint.download(
                                result.getDownloadLocatorReference(),
                                result.getArtifactAuthority()
                        ),
                        handle.getValue()
                );
            case EMPTY:
                if (result.provesAuthoritativeEmptyFor(intent, handle)) {
                    return AdvanceResult.succeeded();
                }
                return transitions.retryUntyped(
                        context,
                        checkpoint,
                        handle.getValue(),
                        "POLL_EMPTY_PROOF_INVALID"
                );
            case TERMINAL_FAILURE:
                return transitions.waitingRecreate(
                        checkpoint,
                        result.getSanitizedCode()
                );
            default:
                return transitions.retryUntyped(
                        context,
                        checkpoint,
                        handle.getValue(),
                        "POLL_STATUS_UNSUPPORTED"
                );
        }
    }
    AdvanceResult download(ExecutionContext context, ExportReportIntent intent,
            ExportReportCheckpoint checkpoint) {
        RemoteExportHandle handle = transitions.taskHandle(context.getTask());
        if (handle == null) {
            return transitions.waitingReconcile(
                    checkpoint.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                    "DOWNLOAD_HANDLE_MISSING"
            );
        }
        String locatorReference = checkpoint.getDownloadLocatorReference();
        ReportArtifactAuthority authority = checkpoint.getArtifactAuthority();
        if (locatorReference == null || authority == null || !authority.proves(intent, handle)) {
            return transitions.waitingPoll(
                    checkpoint.retryAt(ExportReportCheckpoint.Phase.POLL),
                    handle.getValue(),
                    "DOWNLOAD_AUTHORITY_INVALID"
            );
        }
        ProviderOutcome<DownloadedReportArtifact> outcome = transitions.readOutcome(
                () -> provider.download(intent, handle, locatorReference)
        );
        if (outcome.getType() == ProviderOutcomeType.NOT_FOUND) {
            // Signed download locations can expire while the export handle remains valid.
            // Re-polling refreshes the Adapter-owned locator without recreating the export.
            return transitions.waitingPoll(
                    checkpoint.progressTo(ExportReportCheckpoint.Phase.POLL),
                    handle.getValue(),
                    outcome.getSanitizedCode()
            );
        }
        if (outcome.getType() != ProviderOutcomeType.SUCCESS) {
            return transitions.handleReadFailure(
                    context,
                    outcome,
                    checkpoint,
                    handle.getValue(),
                    "DOWNLOAD_OUTCOME_INVALID"
            );
        }
        DownloadedReportArtifact artifact = outcome.getValue();
        if (artifact == null) {
            return transitions.retryUntyped(
                    context,
                    checkpoint,
                    handle.getValue(),
                    "DOWNLOAD_SUCCESS_WITHOUT_ARTIFACT"
            );
        }
        return queued(checkpoint.apply(artifact), handle.getValue());
    }
    AdvanceResult queued(ExportReportCheckpoint checkpoint, String remoteHandle) {
        return transitions.queued(checkpoint, remoteHandle);
    }
    AdvanceResult failure(
            ExportReportCheckpoint checkpoint,
            String remoteHandle,
            String code
    ) {
        return transitions.failure(checkpoint, remoteHandle, code);
    }
}
