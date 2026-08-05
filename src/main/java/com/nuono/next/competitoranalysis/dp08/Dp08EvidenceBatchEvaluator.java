package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.persistence.DataPullTask;
import java.time.LocalDate;

/** Advances at most one 64-member evidence batch for a DP08B task. */
public interface Dp08EvidenceBatchEvaluator {
    Result evaluate(DataPullTask task,Dp08MemberSetHandle handle,LocalDate factDate);

    final class Result {
        private final boolean complete,searchRequired;
        public Result(boolean complete,boolean searchRequired){this.complete=complete;this.searchRequired=searchRequired;}
        public boolean isComplete(){return complete;} public boolean isSearchRequired(){return searchRequired;}
    }
}
