package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.datapull.persistence.DataPullTask;

/** Atomic, fenced writes into the existing competitor facts. */
public interface Dp08FactWriter {
    ApplyResult applyRanking(
            DataPullTask task,
            Dp08KeywordScope scope,
            NoonSearchPage completeTop200
    );

    ApplyResult applyListFound(
            DataPullTask task,
            Dp08ListTarget target,
            NoonProductDetail detail
    );

    ApplyResult applyListNotFound(
            DataPullTask task,
            Dp08ListTarget target,
            NoonSearchPage evidence
    );

    default ApplyResult applyRanking(
            DataPullTask task,Dp08MemberSetHandle handle,NoonSearchPage completeTop200
    ) {
        return applyRanking(task,handle.keywordProviderScope(),completeTop200);
    }

    default ApplyResult applyListFound(
            DataPullTask task,Dp08MemberSetHandle handle,java.time.LocalDate factDate,
            NoonProductDetail detail
    ) {
        return applyListFound(task,handle.listProviderTarget(factDate,true),detail);
    }

    default ApplyResult applyListNotFound(
            DataPullTask task,Dp08MemberSetHandle handle,java.time.LocalDate factDate,
            NoonSearchPage evidence
    ) {
        return applyListNotFound(task,handle.listProviderTarget(factDate,true),evidence);
    }

    enum ApplyResult {
        APPLIED,
        ALREADY_APPLIED,
        MORE
    }
}
