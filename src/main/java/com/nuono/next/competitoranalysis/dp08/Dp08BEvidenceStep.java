package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import java.time.LocalDate;
import java.util.Objects;

/** Advances one bounded evidence batch before an exact DP08-B provider search. */
final class Dp08BEvidenceStep {
    private final Dp08EvidenceBatchEvaluator evaluator;
    private final ProviderWaitTransition waits;

    Dp08BEvidenceStep(
            Dp08EvidenceBatchEvaluator evaluator,
            ProviderWaitTransition waits
    ) {
        this.evaluator = evaluator;
        this.waits = Objects.requireNonNull(waits, "waits");
    }

    AdvanceResult advance(
            DataPullTask task,
            Dp08BTaskContext context,
            LocalDate factDate
    ) {
        if (!context.isMemberSet()) {
            return context.legacyTarget().isExactSearchRequired()
                    ? AdvanceResult.queued(
                            Dp08BExactListBackfillJob.SEARCH_PRIMARY,
                            null,
                            null
                    )
                    : AdvanceResult.succeeded();
        }
        if (evaluator == null) {
            return AdvanceResult.failed(
                    Dp08BExactListBackfillJob.EVALUATE_EVIDENCE,
                    null,
                    task.getCheckpoint(),
                    "DP08B_MEMBER_SET_EVALUATOR_MISSING"
            );
        }
        try {
            Dp08EvidenceBatchEvaluator.Result result = evaluator.evaluate(
                    task,
                    context.memberSetHandle(),
                    factDate
            );
            if (!result.isComplete()) {
                return AdvanceResult.queued(
                        Dp08BExactListBackfillJob.EVALUATE_EVIDENCE,
                        null,
                        null
                );
            }
            return result.isSearchRequired()
                    ? AdvanceResult.queued(
                            Dp08BExactListBackfillJob.SEARCH_PRIMARY,
                            null,
                            null
                    )
                    : AdvanceResult.succeeded();
        } catch (RuntimeException localFailure) {
            return Dp08AdvanceSupport.localRetry(
                    task,
                    OperationCode.DP08B,
                    waits,
                    Dp08BExactListBackfillJob.EVALUATE_EVIDENCE,
                    task.getCheckpoint()
            );
        }
    }
}
