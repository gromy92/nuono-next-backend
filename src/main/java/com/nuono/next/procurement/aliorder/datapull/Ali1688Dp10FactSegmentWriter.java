package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;

/** Provider-fact port for one bounded, whole-item DP10 segment. */
public interface Ali1688Dp10FactSegmentWriter {

    Ali1688Dp10FactSegmentResult applySegment(
            DataPullTask task,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688Dp10ApplySlice slice,
            int maxFactRows
    );
}
