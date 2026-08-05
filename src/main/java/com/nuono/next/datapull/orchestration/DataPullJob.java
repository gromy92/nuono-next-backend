package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.AdmittedDataPullScope;
import java.util.List;

/** Deep Interface implemented once per DP operation. */
public interface DataPullJob {

    OperationCode operationCode();

    String providerChannel();

    String initialStep();

    List<DataPullScope> listScopes();

    /** One source read; persistent preparation is deferred until admission succeeds. */
    default DataPullScopePreparation prepareScopesForEnqueue() {
        return DataPullScopePreparation.readOnly(listScopes());
    }

    /**
     * Completes any slot-specific immutable preparation before the first task insert.
     * Implementations may return only the same input instances, in their original order.
     */
    default List<DataPullScheduledScope> prepareTaskScopesForEnqueue(
            List<DataPullScheduledScope> scheduledScopes,
            List<AdmittedDataPullScope> admittedScopes
    ) {
        return List.copyOf(scheduledScopes);
    }

    AdvanceResult advance(ExecutionContext context);
}
