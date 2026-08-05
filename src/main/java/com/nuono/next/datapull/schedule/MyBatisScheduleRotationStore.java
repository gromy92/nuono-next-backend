package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persistent round-robin cursor; reservation commits before operation work starts. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class MyBatisScheduleRotationStore {
    public static final int MAX_OPERATIONS_PER_TICK = 3;
    private final DataPullScheduleScanMapper mapper;

    public MyBatisScheduleRotationStore(DataPullScheduleScanMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public List<OperationCode> reserve(List<OperationCode> availableOperations) {
        EnumSet<OperationCode> available = EnumSet.copyOf(
                Objects.requireNonNull(availableOperations, "availableOperations")
        );
        if (available.size() != OperationCode.values().length) {
            throw new IllegalStateException("DP_SCHEDULE_OPERATION_REGISTRY_INCOMPLETE");
        }
        ScheduleRotationRow rotation = mapper.lockRotation();
        if (rotation == null || rotation.getNextOperationOrdinal() == null
                || rotation.getVersion() == null) {
            throw new IllegalStateException("DP_SCHEDULE_ROTATION_NOT_INITIALIZED");
        }
        OperationCode[] order = OperationCode.values();
        int start = rotation.getNextOperationOrdinal();
        if (start < 0 || start >= order.length) {
            throw new IllegalStateException("DP_SCHEDULE_ROTATION_CURSOR_INVALID");
        }
        List<OperationCode> result = new ArrayList<>(MAX_OPERATIONS_PER_TICK);
        for (int offset = 0;
                offset < order.length && result.size() < MAX_OPERATIONS_PER_TICK;
                offset++) {
            OperationCode operation = order[(start + offset) % order.length];
            if (available.contains(operation)) result.add(operation);
        }
        int next = (start + result.size()) % order.length;
        if (mapper.advanceRotation(next, rotation.getVersion()) != 1) {
            throw new IllegalStateException("rotation CAS must affect one row");
        }
        return List.copyOf(result);
    }
}
