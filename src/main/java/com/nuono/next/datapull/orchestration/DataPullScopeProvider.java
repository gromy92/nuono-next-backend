package com.nuono.next.datapull.orchestration;

import java.util.List;

/** Discovers immutable scopes for one provider-bound DP Implementation. */
public interface DataPullScopeProvider {

    List<DataPullScope> listScopes();
}
