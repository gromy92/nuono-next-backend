package com.nuono.next.competitoranalysis;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.competitoranalysis.dp08.Dp08FactWriter;
import com.nuono.next.competitoranalysis.dp08.Dp08KeywordScope;
import com.nuono.next.competitoranalysis.dp08.Dp08TrackedProduct;
import com.nuono.next.competitoranalysis.dp08.Dp08MemberSetHandle;
import com.nuono.next.competitoranalysis.dp08.Dp08MemberSetItem;
import com.nuono.next.competitoranalysis.dp08.Dp08TaskMemberProgress;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.Dp08RuntimeMapper;
import com.nuono.next.infrastructure.mapper.Dp08MemberSetMapper;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/** Atomic rank page application with schedule-slot idempotency. */
@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class Dp08RankingFactTransaction {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String TRIGGER_MODE = "DP08_RUNTIME_RANK";
    private final Dp08RuntimeMapper runtimeMapper;
    private final CompetitorAnalysisMapper mapper;
    private final Dp08ImmutableRankingPageWriter pageWriter;
    private final Dp08FactFence fence;
    private final Dp08MemberSetMapper members;

    @Autowired
    public Dp08RankingFactTransaction(
            Dp08RuntimeMapper runtimeMapper,
            CompetitorAnalysisMapper mapper,
            Dp08ImmutableRankingPageWriter pageWriter,
            Dp08MemberSetMapper members
    ) {
        this.runtimeMapper = runtimeMapper;
        this.mapper = mapper;
        this.pageWriter = pageWriter;
        this.fence = new Dp08FactFence(runtimeMapper);
        this.members=members;
    }

    Dp08RankingFactTransaction(Dp08RuntimeMapper runtimeMapper,
            CompetitorAnalysisMapper mapper,Dp08ImmutableRankingPageWriter pageWriter) {
        this(runtimeMapper,mapper,pageWriter,null);
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Dp08FactWriter.ApplyResult apply(
            DataPullTask task,
            Dp08KeywordScope scope,
            NoonSearchPage page
    ) {
        fence.require(task, OperationCode.DP08A);
        LocalDateTime scheduleSlot = scheduleSlotShanghai(task);
        if (runtimeMapper.selectAppliedKeywordRun(scope.getKeywordId(), scheduleSlot) != null) {
            return Dp08FactWriter.ApplyResult.ALREADY_APPLIED;
        }

        CompetitorWatchProductRow watch = boundWatch(scope);
        CompetitorKeywordRow keyword = boundKeyword(scope);
        LocalDateTime capturedAt = page.getCapturedAt();
        if (capturedAt == null) {
            capturedAt = LocalDateTime.now(SHANGHAI);
            page.setCapturedAt(capturedAt);
        }
        long runId = mapper.nextSearchRunId();
        long keywordRunId = mapper.nextKeywordRunId();
        insertSearchRun(task, runId, watch.getId());
        CompetitorKeywordRefreshOutcome outcome = pageWriter.apply(
                CompetitorKeywordRefreshContext.builder()
                        .taskId(task.getId())
                        .searchRunId(runId)
                        .keywordRunId(keywordRunId)
                        .watchProduct(watch)
                        .keyword(keyword)
                        .actorUserId(null)
                        .build(),
                page,
                scope.getTrackedProducts()
        );
        requireSingle(mapper.insertKeywordRun(keywordRun(
                keywordRunId, runId, keyword, outcome, capturedAt
        )), "keyword run insert");
        requireSingle(runtimeMapper.completeRankSearchRun(
                runId,
                value(outcome.getCandidateUpsertedCount()),
                value(outcome.getRankFactWrittenCount())
        ), "search run completion");
        fence.requireStillLive(task);
        return Dp08FactWriter.ApplyResult.APPLIED;
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Dp08FactWriter.ApplyResult apply(
            DataPullTask task,Dp08MemberSetHandle handle,NoonSearchPage page
    ) {
        if(members==null)throw new IllegalStateException("DP08 member-set mapper is unavailable");
        fence.require(task,OperationCode.DP08A);
        members.insertTaskProgress(task.getId(),OperationCode.DP08A,handle.getMemberSetId());
        Dp08TaskMemberProgress progress=java.util.Objects.requireNonNull(
                members.lockTaskProgress(task.getId()),"DP08 progress");
        if(progress.getOperationCode()!=OperationCode.DP08A
                ||!progress.getMemberSetId().equals(handle.getMemberSetId()))
            throw new IllegalStateException("DP08A progress identity drift");
        if(Boolean.TRUE.equals(progress.getApplyComplete()))return Dp08FactWriter.ApplyResult.ALREADY_APPLIED;
        if(page.getCapturedAt()==null)page.setCapturedAt(LocalDateTime.now(SHANGHAI));
        long searchRunId=progress.getSearchRunId()==null?mapper.nextSearchRunId():progress.getSearchRunId();
        long keywordRunId=progress.getKeywordRunId()==null?mapper.nextKeywordRunId():progress.getKeywordRunId();
        CompetitorWatchProductRow watch=boundWatch(handle);
        CompetitorKeywordRow keyword=boundKeyword(handle);
        if(progress.getSearchRunId()==null){insertSearchRun(task,searchRunId,watch.getId());
            pageWriter.initialize(context(task,searchRunId,keywordRunId,watch,keyword),page);}
        List<Dp08MemberSetItem> memberPage=List.copyOf(members.listMemberItemsAfter(
                handle.getMemberSetId(),progress.getApplyCursor(),65));
        if(memberPage.isEmpty()||memberPage.size()>65)throw new IllegalStateException("DP08A apply member page invalid");
        boolean more=memberPage.size()>64;List<Dp08MemberSetItem> selected=memberPage.subList(0,Math.min(64,memberPage.size()));
        List<Dp08TrackedProduct> tracked=new ArrayList<>(selected.size());
        for(Dp08MemberSetItem item:selected)tracked.add(item.trackedProduct());
        CompetitorKeywordRefreshOutcome outcome=pageWriter.applyMembers(
                context(task,searchRunId,keywordRunId,watch,keyword),page,tracked);
        long count=Math.addExact(progress.getAppliedMemberCount(),selected.size());
        if((more&&count>=handle.getMemberCount())||(!more&&count!=handle.getMemberCount()))
            throw new IllegalStateException("DP08A apply count drift");
        int totalRanks=Math.addExact(progress.getRankFactCount(),value(outcome.getRankFactWrittenCount()));
        if(!more){outcome.setRankFactWrittenCount(totalRanks);requireSingle(mapper.insertKeywordRun(keywordRun(
                    keywordRunId,searchRunId,keyword,outcome,page.getCapturedAt())),"keyword run insert");
            requireSingle(runtimeMapper.completeRankSearchRun(searchRunId,0,totalRanks),"search run completion");}
        requireSingle(members.advanceTaskApply(task.getId(),OperationCode.DP08A,progress.getVersion(),
                progress.getApplyCursor(),progress.getAppliedMemberCount(),selected.get(selected.size()-1).getMemberKey(),
                count,!more,totalRanks,searchRunId,keywordRunId),"member apply progress");
        fence.requireStillLive(task);return more?Dp08FactWriter.ApplyResult.MORE:Dp08FactWriter.ApplyResult.APPLIED;
    }

    private static CompetitorKeywordRefreshContext context(DataPullTask task,long searchRunId,long keywordRunId,
            CompetitorWatchProductRow watch,CompetitorKeywordRow keyword){return CompetitorKeywordRefreshContext.builder()
            .taskId(task.getId()).searchRunId(searchRunId).keywordRunId(keywordRunId).watchProduct(watch)
            .keyword(keyword).actorUserId(null).build();}

    private void insertSearchRun(DataPullTask task, long runId, long watchProductId) {
        CompetitorSearchRunInsertCommand command = new CompetitorSearchRunInsertCommand();
        command.setId(runId);
        command.setWatchProductId(watchProductId);
        command.setTaskId(task.getId());
        command.setTriggerMode(TRIGGER_MODE);
        command.setStatus("RUNNING");
        command.setKeywordTotal(1);
        requireSingle(mapper.insertSearchRun(command), "search run insert");
    }

    private CompetitorKeywordRunInsertCommand keywordRun(
            long id,
            long runId,
            CompetitorKeywordRow keyword,
            CompetitorKeywordRefreshOutcome outcome,
            LocalDateTime capturedAt
    ) {
        CompetitorKeywordRunInsertCommand command = new CompetitorKeywordRunInsertCommand();
        command.setId(id);
        command.setSearchRunId(runId);
        command.setKeywordId(keyword.getId());
        command.setKeywordSnapshot(keyword.getKeyword());
        command.setLocaleSnapshot(keyword.getLocale());
        command.setProviderStatus("SUCCESS");
        command.setResultCount(value(outcome.getResultCount()));
        command.setRequestedResultLimit(200);
        command.setSourceUrl(outcome.getSourceUrl());
        command.setParserVersion(outcome.getParserVersion());
        command.setProviderHttpStatus(outcome.getProviderHttpStatus());
        command.setResponseHash(outcome.getResponseHash());
        command.setCapturedAt(capturedAt);
        return command;
    }

    private static CompetitorWatchProductRow boundWatch(Dp08KeywordScope scope) {
        CompetitorWatchProductRow watch = new CompetitorWatchProductRow();
        watch.setId(scope.getWatchProductId());
        watch.setOwnerUserId(scope.getOwnerUserId());
        watch.setLogicalStoreId(scope.getLogicalStoreId());
        watch.setStoreCode(scope.getStoreCode());
        watch.setSiteCode(scope.getSiteCode());
        watch.setSelfNoonProductCode(scope.getTrackedProducts().stream()
                .filter((product) -> product.getSubjectType() == Dp08TrackedProduct.SubjectType.SELF)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("DP-08-A SELF identity is missing"))
                .getNoonProductCode());
        watch.setStatus("ACTIVE");
        return watch;
    }

    private static CompetitorWatchProductRow boundWatch(Dp08MemberSetHandle handle){
        CompetitorWatchProductRow watch=new CompetitorWatchProductRow();watch.setId(handle.getWatchProductId());
        watch.setOwnerUserId(handle.getOwnerUserId());watch.setLogicalStoreId(handle.getLogicalStoreId());
        watch.setStoreCode(handle.getStoreCode());watch.setSiteCode(handle.getSiteCode());
        watch.setSelfNoonProductCode(handle.getNoonProductCode());watch.setStatus("ACTIVE");return watch;}

    private static CompetitorKeywordRow boundKeyword(Dp08KeywordScope scope) {
        CompetitorKeywordRow keyword = new CompetitorKeywordRow();
        keyword.setId(scope.getKeywordId());
        keyword.setWatchProductId(scope.getWatchProductId());
        keyword.setKeyword(scope.getKeyword());
        keyword.setLocale(scope.getLocale());
        keyword.setStatus("ACTIVE");
        return keyword;
    }

    private static CompetitorKeywordRow boundKeyword(Dp08MemberSetHandle handle){CompetitorKeywordRow keyword=new CompetitorKeywordRow();
        keyword.setId(handle.getKeywordId());keyword.setWatchProductId(handle.getWatchProductId());keyword.setKeyword(handle.getKeyword());
        keyword.setLocale(handle.getLocale());keyword.setStatus("ACTIVE");return keyword;}

    private static LocalDateTime scheduleSlotShanghai(DataPullTask task) {
        if (task.getScheduleSlot() == null) {
            throw new IllegalStateException("DP-08-A schedule slot is missing");
        }
        return task.getScheduleSlot().toInstant(ZoneOffset.UTC)
                .atZone(SHANGHAI).toLocalDateTime();
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static void requireSingle(int changed, String action) {
        if (changed != 1) {
            throw new IllegalStateException("DP-08 " + action + " must affect exactly one row");
        }
    }
}
