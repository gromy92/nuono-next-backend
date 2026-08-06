package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import java.util.List;
import java.util.Set;

/** Deep seam for attaching one Module's immutable temporal payloads to scheduled tasks. */
public interface ScheduleTaskPayloadBinder {

    Set<OperationCode> operations();

    void bind(
            OperationCode operation,
            List<DataPullTask> tasks,
            List<ScheduleTaskBindingRow> temporalBindings
    );
}
