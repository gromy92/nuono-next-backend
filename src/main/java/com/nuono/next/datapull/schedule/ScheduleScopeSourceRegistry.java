package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.runtime.OperationCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Exactly one bounded source Adapter per daily operation. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class ScheduleScopeSourceRegistry {

    private final Map<OperationCode, ScheduleScopeSource> sources;

    public ScheduleScopeSourceRegistry(List<ScheduleScopeSource> values) {
        Map<OperationCode, ScheduleScopeSource> result = new EnumMap<>(OperationCode.class);
        for (ScheduleScopeSource source : List.copyOf(Objects.requireNonNull(values, "sources"))) {
            ScheduleScopeSource value = Objects.requireNonNull(source, "source");
            for (OperationCode operation : value.operations()) {
                if (result.putIfAbsent(operation, value) != null) {
                    throw new IllegalArgumentException("duplicate bounded source for " + operation);
                }
            }
        }
        this.sources = Map.copyOf(result);
    }

    public ScheduleScopeSource require(OperationCode operation) {
        ScheduleScopeSource source = sources.get(Objects.requireNonNull(operation, "operation"));
        if (source == null) {
            throw new IllegalStateException("DP_BOUNDED_SOURCE_MISSING:" + operation);
        }
        return source;
    }

    public void requireComplete() {
        for (OperationCode operation : OperationCode.values()) require(operation);
    }
}
