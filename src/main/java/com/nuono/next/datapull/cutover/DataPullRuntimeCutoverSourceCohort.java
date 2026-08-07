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
    private final Map<OperationCode, Map<String, LocalDateTime>> legacyBoundaries;

    DataPullRuntimeCutoverSourceCohort(
            LocalDateTime observedAtUtc,
            DataPullJobRegistry jobs,
            Map<OperationCode, List<DataPullScopeBindingCandidate>> bindings,
            Map<OperationCode, Map<String, LocalDateTime>> legacyBoundaries
    ) {
        this.observedAtUtc = observedAtUtc;
        this.jobs = jobs;
        EnumMap<OperationCode, List<DataPullScopeBindingCandidate>> copied =
                new EnumMap<>(OperationCode.class);
        bindings.forEach((operation, values) -> copied.put(operation, List.copyOf(values)));
        this.bindings = Map.copyOf(copied);
        EnumMap<OperationCode, Map<String, LocalDateTime>> boundaries =
                new EnumMap<>(OperationCode.class);
        legacyBoundaries.forEach((operation, values) ->
                boundaries.put(operation, Map.copyOf(values)));
        this.legacyBoundaries = Map.copyOf(boundaries);
    }

    DataPullRuntimeCutoverSourceCohort(
            LocalDateTime observedAtUtc,
            DataPullJobRegistry jobs,
            Map<OperationCode, List<DataPullScopeBindingCandidate>> bindings
    ) {
        this(observedAtUtc, jobs, bindings, Map.of());
    }

    LocalDateTime getObservedAtUtc() { return observedAtUtc; }
    DataPullJobRegistry getJobs() { return jobs; }
    List<DataPullScopeBindingCandidate> bindings(OperationCode operation) {
        return bindings.getOrDefault(operation, List.of());
    }
    LocalDateTime reconcileAfter(
            OperationCode operation,
            String scopeKey,
            LocalDateTime fallback
    ) {
        return legacyBoundaries.getOrDefault(operation, Map.of())
                .getOrDefault(scopeKey, fallback);
    }
}
