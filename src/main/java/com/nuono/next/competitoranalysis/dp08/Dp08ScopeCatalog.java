package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopePreparation;
import java.time.LocalDate;
import java.util.List;

/** Current active DP-08 identities; task scope keys remain opaque to handlers. */
public interface Dp08ScopeCatalog {
    List<DataPullScope> listKeywordScopes();

    default DataPullScopePreparation prepareKeywordScopesForEnqueue() {
        return DataPullScopePreparation.readOnly(listKeywordScopes());
    }

    List<DataPullScope> listListTargetScopes(LocalDate factDate);

    default Dp08ListTargetPreparation prepareListTargetScopesForEnqueue(LocalDate factDate) {
        return Dp08ListTargetPreparation.readOnly(listListTargetScopes(factDate));
    }
}
