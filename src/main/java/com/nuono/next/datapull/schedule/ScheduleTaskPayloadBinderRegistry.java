package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable operation-to-binder registry; configured payload operations are mandatory. */
public final class ScheduleTaskPayloadBinderRegistry {
    private final Map<OperationCode, ScheduleTaskPayloadBinder> binders;

    public ScheduleTaskPayloadBinderRegistry(List<ScheduleTaskPayloadBinder> values) {
        Map<OperationCode, ScheduleTaskPayloadBinder> registered =
                new EnumMap<>(OperationCode.class);
        for (ScheduleTaskPayloadBinder candidate : List.copyOf(
                Objects.requireNonNull(values, "binders")
        )) {
            ScheduleTaskPayloadBinder binder = Objects.requireNonNull(candidate, "binder");
            Set<OperationCode> operations = Set.copyOf(Objects.requireNonNull(
                    binder.operations(),
                    "binder operations"
            ));
            if (operations.isEmpty()) {
                throw new IllegalArgumentException(
                        "schedule task payload binder has no operation"
                );
            }
            for (OperationCode operation : operations) {
                Objects.requireNonNull(operation, "binder operation");
                if (registered.putIfAbsent(operation, binder) != null) {
                    throw new IllegalArgumentException(
                            "duplicate schedule task payload binder for " + operation
                    );
                }
            }
        }
        binders = Map.copyOf(registered);
    }

    public ScheduleTaskPayloadBinder require(OperationCode operation) {
        OperationCode key = Objects.requireNonNull(operation, "operation");
        ScheduleTaskPayloadBinder binder = binders.get(key);
        if (binder == null) {
            throw new IllegalStateException("DP_SCHEDULE_PAYLOAD_BINDER_MISSING:" + key);
        }
        return binder;
    }

}
