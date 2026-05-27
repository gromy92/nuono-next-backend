package com.nuono.next.aiopsdashboard;

import java.util.List;

public interface AiOperationsDashboardSignalProvider {

    List<AiOperationsDashboardOverview.Signal> collect(AiOperationsDashboardQuery query);

    default AiOperationsDashboardContribution contribute(
            AiOperationsDashboardQuery query,
            AiOperationsDashboardScope scope
    ) {
        return AiOperationsDashboardContribution.signals(collect(query));
    }
}
