package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskRepairCommand;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

/** Explicit repair seam; it never resets a checkpoint or retries an unknown external write. */
public final class DataPullTaskRepairService {
    private static final EnumSet<OperationCode> PRESERVING_REPAIR_OPERATIONS = EnumSet.of(
            OperationCode.DP01,
            OperationCode.DP02,
            OperationCode.DP03,
            OperationCode.DP06,
            OperationCode.DP07B,
            OperationCode.DP08B
    );

    private final DataPullTaskStore store;
    private final Clock clock;

    public DataPullTaskRepairService(DataPullTaskStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<DataPullTask> repair(DataPullTaskRepairCommand command) {
        DataPullTaskRepairCommand request = Objects.requireNonNull(command, "command");
        Optional<DataPullTask> current = store.find(request.getTaskId());
        if (current.isEmpty()) {
            return Optional.empty();
        }
        requireSafeRepair(current.get(), request);
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return store.repairFailed(request, nowUtc);
    }

    private void requireSafeRepair(DataPullTask task, DataPullTaskRepairCommand command) {
        if (task.getState() != TaskState.FAILED
                || !Objects.equals(task.getVersion(), command.getExpectedVersion())
                || !Objects.equals(task.getFenceEpoch(), command.getExpectedFenceEpoch())
                || !Objects.equals(task.getSanitizedFailureCode(), command.getExpectedFailureCode())) {
            throw new IllegalStateException("failed repair command does not match current task fence");
        }
        if (!PRESERVING_REPAIR_OPERATIONS.contains(task.getOperationCode())) {
            throw new IllegalArgumentException("operation requires replacement or operation-specific repair");
        }
        String code = task.getSanitizedFailureCode();
        if (code.contains("OUTCOME_UNKNOWN") || code.contains("UNKNOWN_EXTERNAL_WRITE")) {
            throw new IllegalArgumentException("unknown external-write outcomes cannot be generically repaired");
        }
    }
}
