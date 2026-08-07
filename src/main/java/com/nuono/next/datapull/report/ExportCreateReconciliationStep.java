package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import java.util.Objects;

/** Resolves an ambiguous create without ever treating an ordinary NOT_FOUND as safe to replay. */
final class ExportCreateReconciliationStep {
    private final ExportReportProvider provider;
    private final ExportReportTransitions transitions;

    ExportCreateReconciliationStep(
            ExportReportProvider provider,
            ExportReportTransitions transitions
    ) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.transitions = Objects.requireNonNull(transitions, "transitions");
    }

    AdvanceResult advance(
            ExecutionContext context,
            ExportReportIntent intent,
            ExportReportCheckpoint checkpoint
    ) {
        ProviderOutcome<ExportCreateReadback> outcome = transitions.readOutcome(
                () -> provider.findByRequestKey(intent)
        );
        if (outcome.getType() == ProviderOutcomeType.SUCCESS) {
            return success(context, intent, checkpoint, outcome.getValue());
        }
        if (outcome.getType() == ProviderOutcomeType.NOT_FOUND) {
            if (checkpoint.isCreateOutcomeUnknown()) {
                return transitions.waitingReconcile(
                        checkpoint.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                        "CREATE_UNKNOWN_NOT_FOUND_FAIL_CLOSED"
                );
            }
            return transitions.queued(
                    checkpoint.preserveAttemptAt(ExportReportCheckpoint.Phase.CREATE),
                    null
            );
        }
        if (checkpoint.isCreateOutcomeUnknown()
                && outcome.getType() == ProviderOutcomeType.CONTRACT_ERROR) {
            if (provider.retryUnknownCreateAfterReadbackFailure()) {
                return transitions.waitingRecreate(
                        checkpoint,
                        "READ_ONLY_EXPORT_RETRY_AFTER_RECONCILE"
                );
            }
            // This contract error describes the readback capability, not the outcome of
            // the already attempted create. It cannot prove that replay is safe or that
            // the unknown create failed, so keep the exact intent in reconciliation.
            return transitions.waitingReconcile(
                    checkpoint.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                    outcome.getSanitizedCode()
            );
        }
        return transitions.handleReadFailure(
                context,
                outcome,
                checkpoint,
                null,
                "LOOKUP_OUTCOME_INVALID"
        );
    }

    private AdvanceResult success(
            ExecutionContext context,
            ExportReportIntent intent,
            ExportReportCheckpoint checkpoint,
            ExportCreateReadback readback
    ) {
        if (readback == null || !readback.proves(intent)) {
            return transitions.retryUntyped(
                    context,
                    checkpoint.preserveAttemptAt(
                            ExportReportCheckpoint.Phase.RECONCILE_CREATE
                    ),
                    null,
                    "LOOKUP_AUTHORITY_INVALID"
            );
        }
        if (readback.getStatus() == ExportCreateReadback.Status.FOUND) {
            return transitions.waitingPoll(
                    checkpoint.progressTo(ExportReportCheckpoint.Phase.POLL),
                    readback.getHandle().getValue(),
                    "EXPORT_RECONCILED"
            );
        }
        if (readback.getStatus() == ExportCreateReadback.Status.TERMINALLY_ABSENT) {
            // The provider proof is not bound to this exact create attempt. Reusing it
            // could authorize a second or later create after an ambiguous response.
            return transitions.waitingReconcile(
                    checkpoint.retryAt(ExportReportCheckpoint.Phase.RECONCILE_CREATE),
                    "CREATE_ABSENCE_PROOF_UNBOUND_FAIL_CLOSED"
            );
        }
        return transitions.retryUntyped(
                context,
                checkpoint,
                null,
                "LOOKUP_STATUS_INVALID"
        );
    }
}
