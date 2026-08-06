package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;

/** Deep persistence Interface for sealed cutover anchors and bounded new-scope admission. */
public interface DataPullScheduleAnchorStore {

    Cohort open(OperationCode operationCode);

    /** One manifest-verified operation cohort for a single reconciliation pass. */
    interface Cohort {
        LocalDateTime reconcileAfterUtc(AdmittedDataPullScope admittedScope);
    }

    static DataPullScheduleAnchorStore failClosed() {
        return operationCode -> {
            throw new IllegalStateException(
                    "DP_SCHEDULE_CUTOVER_ANCHOR_STORE_NOT_WIRED:" + operationCode
            );
        };
    }
}
