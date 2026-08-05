package com.nuono.next.datapull.cutover;

import com.nuono.next.datapull.orchestration.DataPullJobRegistry;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** One repeatable-read observation of every operation scope and immutable DP08 payload. */
final class DataPullRuntimeCutoverSourceCohort {
    private final LocalDateTime observedAtUtc;
    private final DataPullJobRegistry jobs;
    private final Map<OperationCode, List<DataPullScopeBindingCandidate>> bindings;

    DataPullRuntimeCutoverSourceCohort(
            LocalDateTime observedAtUtc,
            DataPullJobRegistry jobs,
            Map<OperationCode, List<DataPullScopeBindingCandidate>> bindings
    ) {
        this.observedAtUtc = observedAtUtc;
        this.jobs = jobs;
        EnumMap<OperationCode, List<DataPullScopeBindingCandidate>> copied =
                new EnumMap<>(OperationCode.class);
        bindings.forEach((operation, values) -> copied.put(operation, List.copyOf(values)));
        this.bindings = Map.copyOf(copied);
    }

    LocalDateTime getObservedAtUtc() { return observedAtUtc; }
    DataPullJobRegistry getJobs() { return jobs; }
    List<DataPullScopeBindingCandidate> bindings(OperationCode operation) {
        return bindings.getOrDefault(operation, List.of());
    }
}
