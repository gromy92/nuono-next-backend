package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.dp08.Dp08FactWriter;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.competitoranalysis.dp08.Dp08KeywordScope;
import com.nuono.next.competitoranalysis.dp08.Dp08ListTarget;
import com.nuono.next.competitoranalysis.dp08.Dp08MemberSetHandle;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.datapull.persistence.DataPullTask;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Small facade keeping each DP-08 fact boundary in its own transactional service. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class CompetitorDp08FactWriter implements Dp08FactWriter {
    private final Dp08RankingFactTransaction ranking;
    private final Dp08ListFactTransaction listing;

    public CompetitorDp08FactWriter(
            Dp08RankingFactTransaction ranking,
            Dp08ListFactTransaction listing
    ) {
        this.ranking = ranking;
        this.listing = listing;
    }

    @Override
    public ApplyResult applyRanking(
            DataPullTask task,
            Dp08KeywordScope scope,
            NoonSearchPage completeTop200
    ) {
        return ranking.apply(task, scope, completeTop200);
    }

    @Override
    public ApplyResult applyListFound(
            DataPullTask task,
            Dp08ListTarget target,
            NoonProductDetail detail
    ) {
        return listing.applyFound(task, target, detail);
    }

    @Override
    public ApplyResult applyListNotFound(
            DataPullTask task,
            Dp08ListTarget target,
            NoonSearchPage evidence
    ) {
        return listing.applyNotFound(task, target, evidence);
    }

    @Override
    public ApplyResult applyRanking(
            DataPullTask task,Dp08MemberSetHandle handle,NoonSearchPage completeTop200
    ) {
        return ranking.apply(task,handle,completeTop200);
    }

    @Override
    public ApplyResult applyListFound(
            DataPullTask task,Dp08MemberSetHandle handle,java.time.LocalDate factDate,
            NoonProductDetail detail
    ) {
        return listing.applyFound(task,handle,factDate,detail);
    }

    @Override
    public ApplyResult applyListNotFound(
            DataPullTask task,Dp08MemberSetHandle handle,java.time.LocalDate factDate,
            NoonSearchPage evidence
    ) {
        return listing.applyNotFound(task,handle,factDate,evidence);
    }
}
