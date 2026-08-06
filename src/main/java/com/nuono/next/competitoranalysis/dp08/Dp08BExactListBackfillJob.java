package com.nuono.next.competitoranalysis.dp08;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.DataPullScheduledScope;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.schedule.AdmittedDataPullScope;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Routes DP08-B evidence, provider, and fact steps without owning their behavior. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class Dp08BExactListBackfillJob implements DataPullJob {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    static final String EVALUATE_EVIDENCE = "EVALUATE_LIST_EVIDENCE";
    static final String SEARCH_PRIMARY = "SEARCH_PRIMARY_LIST";
    static final String SEARCH_ALTERNATE = "SEARCH_ALTERNATE_LIST";
    static final String APPLY_FOUND = "APPLY_LIST_FOUND";
    static final String APPLY_NOT_FOUND = "APPLY_LIST_NOT_FOUND";

    private final Dp08BTaskScopeBinder scopeBinder;
    private final Dp08BTaskContextDecoder contextDecoder;
    private final Dp08BEvidenceStep evidenceStep;
    private final Dp08BTaskSteps taskSteps;

    @Autowired
    public Dp08BExactListBackfillJob(
            Dp08ScopeCatalog scopes,
            Dp08SearchProvider provider,
            Dp08FactWriter writer,
            ObjectMapper objectMapper,
            ProviderWaitTransition waits,
            Dp08EvidenceBatchEvaluator evidence
    ) {
        this(
                scopes,
                provider,
                writer,
                objectMapper,
                waits,
                Clock.systemUTC(),
                evidence
        );
    }

    Dp08BExactListBackfillJob(
            Dp08ScopeCatalog scopes,
            Dp08SearchProvider provider,
            Dp08FactWriter writer,
            ObjectMapper objectMapper,
            ProviderWaitTransition waits,
            Clock clock
    ) {
        this(scopes, provider, writer, objectMapper, waits, clock, null);
    }

    private Dp08BExactListBackfillJob(
            Dp08ScopeCatalog scopes,
            Dp08SearchProvider provider,
            Dp08FactWriter writer,
            ObjectMapper objectMapper,
            ProviderWaitTransition waits,
            Clock clock,
            Dp08EvidenceBatchEvaluator evidence
    ) {
        this.scopeBinder = new Dp08BTaskScopeBinder(scopes, clock);
        this.contextDecoder = new Dp08BTaskContextDecoder(objectMapper);
        this.evidenceStep = new Dp08BEvidenceStep(evidence, waits);
        this.taskSteps = new Dp08BTaskSteps(provider, writer, objectMapper, waits);
    }

    @Override
    public OperationCode operationCode() {
        return OperationCode.DP08B;
    }

    @Override
    public String providerChannel() {
        return Dp08AKeywordRankingJob.PROVIDER_CHANNEL;
    }

    @Override
    public String initialStep() {
        return EVALUATE_EVIDENCE;
    }

    @Override
    public List<DataPullScope> listScopes() {
        return scopeBinder.listCurrentScopes();
    }

    @Override
    public List<DataPullScheduledScope> prepareTaskScopesForEnqueue(
            List<DataPullScheduledScope> scheduledScopes,
            List<AdmittedDataPullScope> admittedScopes
    ) {
        return scopeBinder.prepare(scheduledScopes, admittedScopes);
    }

    @Override
    public AdvanceResult advance(ExecutionContext context) {
        DataPullTask task = requireTask(context);
        LocalDate factDate = businessDate(task);
        Dp08BTaskContext taskContext;
        try {
            taskContext = contextDecoder.decode(task, factDate);
        } catch (RuntimeException invalidSnapshot) {
            return AdvanceResult.failed(
                    task.getStepCode(),
                    null,
                    task.getCheckpoint(),
                    "DP08B_SCOPE_SNAPSHOT_INVALID"
            );
        }

        String step = task.getStepCode();
        if (EVALUATE_EVIDENCE.equals(step)) {
            return evidenceStep.advance(task, taskContext, factDate);
        }
        if (SEARCH_PRIMARY.equals(step)) {
            if (!taskContext.isMemberSet()
                    && !taskContext.legacyTarget().isExactSearchRequired()) {
                return AdvanceResult.succeeded();
            }
            return taskSteps.searchPrimary(task, taskContext, factDate);
        }
        if (SEARCH_ALTERNATE.equals(step)) {
            return taskSteps.searchAlternate(task, taskContext, factDate);
        }
        if (APPLY_FOUND.equals(step) || APPLY_NOT_FOUND.equals(step)) {
            return taskSteps.apply(task, taskContext, factDate, step);
        }
        return AdvanceResult.failed(
                step,
                null,
                task.getCheckpoint(),
                "DP08B_STEP_INVALID"
        );
    }

    private DataPullTask requireTask(ExecutionContext context) {
        DataPullTask task = Objects.requireNonNull(context, "context").getTask();
        if (task.getOperationCode() != OperationCode.DP08B) {
            throw new IllegalArgumentException("DP08-B received a task for another operation");
        }
        return task;
    }

    private LocalDate businessDate(DataPullTask task) {
        if (task.getScheduleSlot() == null) {
            throw new IllegalStateException("DP08-B schedule slot is missing");
        }
        return task.getScheduleSlot()
                .toInstant(ZoneOffset.UTC)
                .atZone(SHANGHAI)
                .toLocalDate();
    }
}
