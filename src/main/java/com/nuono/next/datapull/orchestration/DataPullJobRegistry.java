package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.OperationCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Registry with exactly zero or one job Implementation per operation. */
public final class DataPullJobRegistry {

    private final Map<OperationCode, DataPullJob> jobs;

    public DataPullJobRegistry(List<DataPullJob> jobs) {
        Objects.requireNonNull(jobs, "jobs");
        Map<OperationCode, DataPullJob> byOperation = new EnumMap<>(OperationCode.class);
        for (DataPullJob job : jobs) {
            DataPullJob nonNull = Objects.requireNonNull(job, "job");
            OperationCode operationCode = Objects.requireNonNull(nonNull.operationCode(), "operationCode");
            DataPullJob previous = byOperation.putIfAbsent(operationCode, nonNull);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate data-pull job for " + operationCode.name());
            }
        }
        this.jobs = Collections.unmodifiableMap(byOperation);
    }

    public Optional<DataPullJob> find(OperationCode operationCode) {
        return Optional.ofNullable(jobs.get(Objects.requireNonNull(operationCode, "operationCode")));
    }

    public DataPullJob require(OperationCode operationCode) {
        return find(operationCode).orElseThrow(
                () -> new IllegalStateException("no data-pull job registered for " + operationCode.name())
        );
    }

    public Collection<DataPullJob> all() {
        return List.copyOf(new ArrayList<>(jobs.values()));
    }

    public void requireComplete() {
        Set<OperationCode> expected = EnumSet.allOf(OperationCode.class);
        if (!jobs.keySet().equals(expected)) {
            Set<OperationCode> missing = EnumSet.copyOf(expected);
            missing.removeAll(jobs.keySet());
            throw new IllegalStateException("missing data-pull jobs: " + missing);
        }
    }
}
