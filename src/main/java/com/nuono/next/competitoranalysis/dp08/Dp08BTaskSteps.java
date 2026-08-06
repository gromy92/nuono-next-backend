package com.nuono.next.competitoranalysis.dp08;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import java.time.LocalDate;
import java.util.Objects;

/** Executes one provider or fact action for the already-decoded DP08-B task context. */
final class Dp08BTaskSteps {
    private final Dp08SearchProvider provider;
    private final Dp08FactWriter writer;
    private final ObjectMapper objectMapper;
    private final ProviderWaitTransition waits;

    Dp08BTaskSteps(
            Dp08SearchProvider provider,
            Dp08FactWriter writer,
            ObjectMapper objectMapper,
            ProviderWaitTransition waits
    ) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy();
        this.waits = Objects.requireNonNull(waits, "waits");
    }

    AdvanceResult searchPrimary(
            DataPullTask task,
            Dp08BTaskContext context,
            LocalDate factDate
    ) {
        Dp08ListTarget target = context.providerTarget(factDate);
        ProviderOutcome<NoonSearchPage> outcome = search(
                context,
                factDate,
                "en-" + target.getSiteCode()
        );
        if (outcome == null) {
            return retry(task, Dp08BExactListBackfillJob.SEARCH_PRIMARY);
        }
        if (outcome.getType() != ProviderOutcomeType.SUCCESS) {
            return providerFailure(
                    task,
                    outcome,
                    Dp08BExactListBackfillJob.SEARCH_PRIMARY
            );
        }
        try {
            NoonSearchPage page = Objects.requireNonNull(
                    outcome.getValue(),
                    "DP08-B successful primary page"
            );
            NoonSearchResult exact = Dp08ListResultSupport.exact(
                    page,
                    target.getNoonProductCode()
            );
            if (exact == null) {
                return AdvanceResult.queued(
                        Dp08BExactListBackfillJob.APPLY_NOT_FOUND,
                        null,
                        Dp08ListCheckpoint.notFound(page).encode(objectMapper)
                );
            }
            NoonProductDetail detail = Dp08ListResultSupport.toDetail(target, page, exact);
            String nextStep = Dp08ListResultSupport.hasCompleteTitles(detail)
                    ? Dp08BExactListBackfillJob.APPLY_FOUND
                    : Dp08BExactListBackfillJob.SEARCH_ALTERNATE;
            return AdvanceResult.queued(
                    nextStep,
                    null,
                    Dp08ListCheckpoint.found(detail, page).encode(objectMapper)
            );
        } catch (RuntimeException invalidPage) {
            return retry(task, Dp08BExactListBackfillJob.SEARCH_PRIMARY);
        }
    }

    AdvanceResult searchAlternate(
            DataPullTask task,
            Dp08BTaskContext context,
            LocalDate factDate
    ) {
        Dp08ListTarget target = context.providerTarget(factDate);
        Dp08ListCheckpoint checkpoint;
        try {
            checkpoint = Dp08ListCheckpoint.decode(objectMapper, task.getCheckpoint());
            if (!"FOUND".equals(checkpoint.getOutcome())) {
                throw new IllegalArgumentException("alternate search requires a found result");
            }
        } catch (RuntimeException invalidCheckpoint) {
            return AdvanceResult.failed(
                    Dp08BExactListBackfillJob.SEARCH_ALTERNATE,
                    null,
                    task.getCheckpoint(),
                    "DP08B_CHECKPOINT_INVALID"
            );
        }
        String locale = Dp08ListResultSupport.missingLocale(
                checkpoint.getDetail(),
                target.getSiteCode()
        );
        ProviderOutcome<NoonSearchPage> outcome = search(context, factDate, locale);
        if (outcome == null) {
            return retry(task, Dp08BExactListBackfillJob.SEARCH_ALTERNATE);
        }
        if (outcome.getType() != ProviderOutcomeType.SUCCESS) {
            return providerFailure(
                    task,
                    outcome,
                    Dp08BExactListBackfillJob.SEARCH_ALTERNATE
            );
        }
        try {
            NoonSearchPage page = Objects.requireNonNull(
                    outcome.getValue(),
                    "DP08-B successful alternate page"
            );
            Dp08ListResultSupport.mergeAlternate(
                    checkpoint.getDetail(),
                    page,
                    Dp08ListResultSupport.exact(page, target.getNoonProductCode())
            );
            return AdvanceResult.queued(
                    Dp08BExactListBackfillJob.APPLY_FOUND,
                    null,
                    Dp08ListCheckpoint.found(
                            checkpoint.getDetail(),
                            checkpoint.getEvidence()
                    ).encode(objectMapper)
            );
        } catch (RuntimeException invalidPage) {
            return retry(task, Dp08BExactListBackfillJob.SEARCH_ALTERNATE);
        }
    }

    AdvanceResult apply(
            DataPullTask task,
            Dp08BTaskContext context,
            LocalDate factDate,
            String step
    ) {
        Dp08ListCheckpoint checkpoint;
        try {
            checkpoint = Dp08ListCheckpoint.decode(objectMapper, task.getCheckpoint());
        } catch (RuntimeException invalidCheckpoint) {
            return AdvanceResult.failed(
                    step,
                    null,
                    task.getCheckpoint(),
                    "DP08B_CHECKPOINT_INVALID"
            );
        }
        if (!matches(step, checkpoint.getOutcome())) {
            return AdvanceResult.failed(
                    step,
                    null,
                    task.getCheckpoint(),
                    "DP08B_CHECKPOINT_STATE_INVALID"
            );
        }
        try {
            Dp08FactWriter.ApplyResult result = applyFact(
                    task,
                    context,
                    factDate,
                    step,
                    checkpoint
            );
            return result == Dp08FactWriter.ApplyResult.MORE
                    ? AdvanceResult.queued(step, null, task.getCheckpoint())
                    : AdvanceResult.succeeded();
        } catch (RuntimeException localFailure) {
            return retry(task, step);
        }
    }

    AdvanceResult retry(DataPullTask task, String step) {
        return Dp08AdvanceSupport.localRetry(
                task,
                OperationCode.DP08B,
                waits,
                step,
                task.getCheckpoint()
        );
    }

    private Dp08FactWriter.ApplyResult applyFact(
            DataPullTask task,
            Dp08BTaskContext context,
            LocalDate factDate,
            String step,
            Dp08ListCheckpoint checkpoint
    ) {
        Dp08FactWriter.ApplyResult result;
        if (Dp08BExactListBackfillJob.APPLY_FOUND.equals(step)) {
            result = context.isMemberSet()
                    ? writer.applyListFound(
                            task,
                            context.memberSetHandle(),
                            factDate,
                            checkpoint.getDetail()
                    )
                    : writer.applyListFound(
                            task,
                            context.legacyTarget(),
                            checkpoint.getDetail()
                    );
        } else {
            result = context.isMemberSet()
                    ? writer.applyListNotFound(
                            task,
                            context.memberSetHandle(),
                            factDate,
                            checkpoint.getEvidence()
                    )
                    : writer.applyListNotFound(
                            task,
                            context.legacyTarget(),
                            checkpoint.getEvidence()
                    );
        }
        return Objects.requireNonNull(result, "DP08-B fact apply result");
    }

    private boolean matches(String step, String outcome) {
        return Dp08BExactListBackfillJob.APPLY_FOUND.equals(step)
                && "FOUND".equals(outcome)
                || Dp08BExactListBackfillJob.APPLY_NOT_FOUND.equals(step)
                && "NOT_FOUND".equals(outcome);
    }

    private ProviderOutcome<NoonSearchPage> search(
            Dp08BTaskContext context,
            LocalDate factDate,
            String locale
    ) {
        try {
            ProviderOutcome<NoonSearchPage> outcome = context.isMemberSet()
                    ? provider.searchExact(context.memberSetHandle(), factDate, locale)
                    : provider.searchExact(context.legacyTarget(), locale);
            return Objects.requireNonNull(outcome, "DP08-B provider outcome");
        } catch (RuntimeException providerFailure) {
            return null;
        }
    }

    private AdvanceResult providerFailure(
            DataPullTask task,
            ProviderOutcome<NoonSearchPage> outcome,
            String step
    ) {
        return Dp08AdvanceSupport.failure(
                task,
                OperationCode.DP08B,
                outcome,
                waits,
                step,
                task.getCheckpoint()
        );
    }
}
