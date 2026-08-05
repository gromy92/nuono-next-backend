package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Instant;
import java.util.Set;

/** DB-bounded source Adapter; cursors are native stable source tuples, never OFFSETs. */
public interface ScheduleScopeSource {

    Set<OperationCode> operations();

    ScheduleSourcePage readPage(
            OperationCode operationCode,
            String afterNativeCursorExclusive,
            Instant reconcileUntil,
            int limit
    );

    default ScheduleSourcePage readPage(ScheduleSourceReadContext context) {
        ScheduleSourceReadContext value = java.util.Objects.requireNonNull(context, "context");
        return readPage(
                value.getOperationCode(), value.getAfterNativeCursorExclusive(),
                value.getReconcileUntil(), value.getLimit()
        );
    }
}
