package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.Dp08SearchPageContract;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopePreparation;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.snapshot.MyBatisSnapshotStageStore;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotStageProof;
import com.nuono.next.datapull.snapshot.SnapshotStageResult;
import com.nuono.next.datapull.snapshot.SnapshotStageStore;
import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** DP-08-A: one keyword task, exactly two bounded page calls, then one atomic fact write. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class Dp08AKeywordRankingJob implements DataPullJob {
    public static final String PROVIDER_CHANNEL = "NOON_FRONTEND_SEARCH";
    static final String FETCH_PAGE_1 = "FETCH_PAGE_1";
    static final String FETCH_PAGE_2 = "FETCH_PAGE_2";
    static final String APPLY = "APPLY_RANK_FACTS";

    private final Dp08ScopeCatalog scopes;
    private final Dp08SearchProvider provider;
    private final Dp08FactWriter factWriter;
    private final SnapshotStageStore<NoonSearchPage> stageStore;
    private final ProviderWaitTransition providerWaitTransition;
    private final Dp08ScopeSnapshotCodec scopeSnapshotCodec;
    private final Dp08MemberSetHandleCodec memberSetCodec;

    @Autowired
    public Dp08AKeywordRankingJob(
            Dp08ScopeCatalog scopes,
            Dp08SearchProvider provider,
            Dp08FactWriter factWriter,
            CompleteSnapshotStageMapper stageMapper,
            ObjectMapper objectMapper,
            ProviderWaitTransition providerWaitTransition
    ) {
        this(
                scopes,
                provider,
                factWriter,
                stageStore(stageMapper, objectMapper),
                providerWaitTransition,
                objectMapper
        );
    }

    Dp08AKeywordRankingJob(
            Dp08ScopeCatalog scopes,
            Dp08SearchProvider provider,
            Dp08FactWriter factWriter,
            SnapshotStageStore<NoonSearchPage> stageStore,
            ProviderWaitTransition providerWaitTransition,
            ObjectMapper objectMapper
    ) {
        this.scopes = Objects.requireNonNull(scopes, "scopes");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.factWriter = Objects.requireNonNull(factWriter, "factWriter");
        this.stageStore = Objects.requireNonNull(stageStore, "stageStore");
        this.providerWaitTransition = Objects.requireNonNull(
                providerWaitTransition,
                "providerWaitTransition"
        );
        this.scopeSnapshotCodec = new Dp08ScopeSnapshotCodec(objectMapper);
        this.memberSetCodec = new Dp08MemberSetHandleCodec(objectMapper);
    }

    @Override
    public OperationCode operationCode() {
        return OperationCode.DP08A;
    }

    @Override
    public String providerChannel() {
        return PROVIDER_CHANNEL;
    }

    @Override
    public String initialStep() {
        return FETCH_PAGE_1;
    }

    @Override
    public List<DataPullScope> listScopes() {
        return scopes.listKeywordScopes();
    }

    @Override
    public DataPullScopePreparation prepareScopesForEnqueue() {
        return scopes.prepareKeywordScopesForEnqueue();
    }

    @Override
    public AdvanceResult advance(ExecutionContext context) {
        DataPullTask task = requireTask(context);
        Dp08KeywordScope scope=null;
        Dp08MemberSetHandle handle=null;
        try {
            if(Dp08MemberSetHandleCodec.KEYWORD_TYPE.equals(task.getScopePayloadType()))
                handle=memberSetCodec.decode(task);
            else scope = scopeSnapshotCodec.decodeKeyword(task);
        } catch (RuntimeException invalidSnapshot) {
            return AdvanceResult.failed(
                    task.getStepCode(), null, task.getCheckpoint(),
                    "DP08A_SCOPE_SNAPSHOT_INVALID"
            );
        }
        String step = task.getStepCode();
        if (FETCH_PAGE_1.equals(step)) {
            return fetchAndStage(task, scope, handle, 1);
        }
        if (FETCH_PAGE_2.equals(step)) {
            return fetchAndStage(task, scope, handle, 2);
        }
        if (APPLY.equals(step)) {
            return apply(task, scope, handle);
        }
        return AdvanceResult.failed(step, null, task.getCheckpoint(), "DP08A_STEP_INVALID");
    }

    private AdvanceResult fetchAndStage(
            DataPullTask task,Dp08KeywordScope scope,Dp08MemberSetHandle handle,int pageNo
    ) {
        ProviderOutcome<NoonSearchPage> outcome;
        try {
            outcome = Objects.requireNonNull(
                    handle==null?provider.fetchRankPage(scope,pageNo):provider.fetchRankPage(handle,pageNo),
                    "DP-08-A provider outcome"
            );
        } catch (RuntimeException untypedFailure) {
            return Dp08AdvanceSupport.localRetry(
                    task,
                    operationCode(),
                    providerWaitTransition,
                    pageNo == 1 ? FETCH_PAGE_1 : FETCH_PAGE_2,
                    task.getCheckpoint()
            );
        }
        if (outcome.getType() != ProviderOutcomeType.SUCCESS) {
            return Dp08AdvanceSupport.failure(
                    task, operationCode(), outcome, providerWaitTransition,
                    pageNo == 1 ? FETCH_PAGE_1 : FETCH_PAGE_2, task.getCheckpoint()
            );
        }
        SnapshotStageResult staged;
        try {
            staged = Objects.requireNonNull(
                    stageStore.stagePage(
                            taskId(task), fence(task), new SnapshotPage<>(
                                    pageNo,
                                    pageNo == 1 ? 2 : null,
                                    pageNo == 2,
                                    2,
                                    List.of(Objects.requireNonNull(
                                            outcome.getValue(),
                                            "DP-08-A successful page"
                                    ))
                            )
                    ),
                    "DP-08-A stage result"
            );
        } catch (RuntimeException untypedFailure) {
            return Dp08AdvanceSupport.localRetry(
                    task,
                    operationCode(),
                    providerWaitTransition,
                    pageNo == 1 ? FETCH_PAGE_1 : FETCH_PAGE_2,
                    task.getCheckpoint()
            );
        }
        if (!staged.isAccepted()) {
            return restart(task, staged.getSanitizedCode());
        }
        return AdvanceResult.queued(pageNo == 1 ? FETCH_PAGE_2 : APPLY, null, null);
    }

    private AdvanceResult apply(
            DataPullTask task,Dp08KeywordScope scope,Dp08MemberSetHandle handle
    ) {
        try {
            SnapshotStageProof<NoonSearchPage> proof = stageStore.proveComplete(
                    taskId(task), fence(task)
            );
            if (!proof.isComplete() || proof.getLastPage().orElse(0) != 2
                    || proof.getItems().size() != 2) {
                return restart(task, proof.getSanitizedCode());
            }
            List<NoonSearchPage> pages = proof.getItems().stream()
                    .sorted(Comparator.comparing(NoonSearchPage::getProviderPage))
                    .collect(Collectors.toList());
            NoonSearchPage complete = Dp08SearchPageContract.mergeRankPages(
                    pages.get(0), pages.get(1)
            );
            Dp08FactWriter.ApplyResult applied = Objects.requireNonNull(
                    handle==null?factWriter.applyRanking(task,scope,complete)
                            :factWriter.applyRanking(task,handle,complete),
                    "DP-08-A fact apply result"
            );
            if(applied==Dp08FactWriter.ApplyResult.MORE)
                return AdvanceResult.queued(APPLY,null,task.getCheckpoint());
            if (applied != Dp08FactWriter.ApplyResult.APPLIED
                    && applied != Dp08FactWriter.ApplyResult.ALREADY_APPLIED) {
                return Dp08AdvanceSupport.localRetry(
                        task, operationCode(), providerWaitTransition, APPLY, task.getCheckpoint()
                );
            }
            if (!stageStore.clear(taskId(task), fence(task))) {
                return Dp08AdvanceSupport.localRetry(
                        task, operationCode(), providerWaitTransition, APPLY, task.getCheckpoint()
                );
            }
            return AdvanceResult.succeeded();
        } catch (NoonSearchProviderException contractFailure) {
            return restart(task, safeCode(contractFailure.getErrorCode()));
        } catch (RuntimeException localFailure) {
            return Dp08AdvanceSupport.localRetry(
                    task, operationCode(), providerWaitTransition, APPLY, task.getCheckpoint()
            );
        }
    }

    private AdvanceResult restart(DataPullTask task, String code) {
        try {
            if (!stageStore.clear(taskId(task), fence(task))) {
                return Dp08AdvanceSupport.localRetry(
                        task, operationCode(), providerWaitTransition, FETCH_PAGE_1, null
                );
            }
        } catch (RuntimeException clearFailure) {
            return Dp08AdvanceSupport.localRetry(
                    task, operationCode(), providerWaitTransition, FETCH_PAGE_1, null
            );
        }
        return Dp08AdvanceSupport.failure(
                task,
                operationCode(),
                ProviderOutcome.transientFailure(code),
                providerWaitTransition,
                FETCH_PAGE_1,
                null
        );
    }

    private static SnapshotStageStore<NoonSearchPage> stageStore(
            CompleteSnapshotStageMapper mapper,
            ObjectMapper objectMapper
    ) {
        Dp08RankPageCodec codec = new Dp08RankPageCodec(objectMapper);
        return new MyBatisSnapshotStageStore<>(mapper, codec, codec);
    }

    private DataPullTask requireTask(ExecutionContext context) {
        DataPullTask task = Objects.requireNonNull(context, "context").getTask();
        if (task.getOperationCode() != operationCode()) {
            throw new IllegalArgumentException("DP-08-A received a task for another operation");
        }
        return task;
    }

    private static long taskId(DataPullTask task) {
        if (task.getId() == null || task.getId() < 1L) {
            throw new IllegalStateException("DP-08-A task id is invalid");
        }
        return task.getId();
    }

    private static long fence(DataPullTask task) {
        if (task.getFenceEpoch() == null || task.getFenceEpoch() < 1L) {
            throw new IllegalStateException("DP-08-A fence is invalid");
        }
        return task.getFenceEpoch();
    }

    private static String safeCode(String value) {
        return value == null || value.isBlank() ? "DP08_RANK_CONTRACT_ERROR" : value;
    }
}
