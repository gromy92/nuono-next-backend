package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeMaintenance;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleEpochRetentionMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Deep Module owning bounded aborted-stage drain and terminal schedule-epoch retention. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class ScheduleEpochRetention implements DataPullRuntimeMaintenance {
    public static final int CHILD_BATCH_SIZE = 64;
    public static final int MAX_CHILD_ROWS_PER_RUN = 1_024;
    public static final int MAX_HEADER_DELETES_PER_RUN = 32;
    public static final int MAX_WORK_STEPS_PER_RUN = 64;
    static final Duration TERMINAL_MAXIMUM_AGE = Duration.ofDays(7);
    static final Duration MAX_RUN_DURATION = Duration.ofMillis(250);

    private final DataPullScheduleEpochRetentionMapper mapper;
    private final LongSupplier nanoTime;
    private int nextOperationOrdinal;

    public ScheduleEpochRetention(DataPullScheduleEpochRetentionMapper mapper) {
        this(mapper, System::nanoTime);
    }

    ScheduleEpochRetention(
            DataPullScheduleEpochRetentionMapper mapper,
            LongSupplier nanoTime
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /** Blocks a replacement scan only while the latest aborted epoch still owns staged children. */
    public boolean drainAbortedBeforeRetry(ScheduleSourceEpochRow epoch) {
        ScheduleSourceEpochRow terminal = requireTerminal(epoch, "ABORTED");
        int deleted = deleteChildren(terminal,CHILD_BATCH_SIZE);
        requireBounded(deleted, CHILD_BATCH_SIZE);
        return deleted > 0;
    }

    @Override
    public synchronized void run(Instant nowUtc) {
        Instant now = Objects.requireNonNull(nowUtc, "nowUtc");
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                now.minus(TERMINAL_MAXIMUM_AGE), ZoneOffset.UTC
        );
        OperationCode[] operations = OperationCode.values();
        long deadline = Math.addExact(nanoTime.getAsLong(), MAX_RUN_DURATION.toNanos());
        int childRows = 0;
        int headers = 0;
        int steps = 0;
        int idleOperations = 0;
        while (steps < MAX_WORK_STEPS_PER_RUN
                && childRows < MAX_CHILD_ROWS_PER_RUN
                && headers < MAX_HEADER_DELETES_PER_RUN
                && nanoTime.getAsLong() < deadline
                && idleOperations < operations.length) {
            int ordinal = nextOperationOrdinal;
            nextOperationOrdinal = (ordinal + 1) % operations.length;
            ScheduleSourceEpochRow candidate = retentionCandidate(
                    operations[ordinal], cutoff
            );
            if (candidate == null) {
                idleOperations++;
                continue;
            }
            idleOperations = 0;
            RetentionWork work = advanceCandidate(
                    candidate,
                    Math.min(CHILD_BATCH_SIZE, MAX_CHILD_ROWS_PER_RUN - childRows)
            );
            childRows += work.childRows;
            headers += work.headerDeleted ? 1 : 0;
            steps++;
        }
    }

    private ScheduleSourceEpochRow retentionCandidate(
            OperationCode operation, LocalDateTime cutoffUtc
    ) {
        ScheduleSourceEpochRow expired = mapper.findExpiredTerminalEpoch(operation, cutoffUtc);
        return expired != null ? expired : mapper.findThirdNewestTerminalEpoch(operation);
    }

    private RetentionWork advanceCandidate(
            ScheduleSourceEpochRow candidate,
            int childLimit
    ) {
        ScheduleSourceEpochRow terminal = requireTerminal(candidate, "COMPLETE", "ABORTED");
        int deleted = deleteChildren(terminal,childLimit);
        requireBounded(deleted, childLimit);
        if (deleted > 0) return new RetentionWork(deleted, false);
        int header = mapper.deleteTerminalEpochIfEmpty(
                terminal.getOperationCode(), terminal.getEpochNo(), terminal.getVersion()
        );
        requireBounded(header, 1);
        return new RetentionWork(0, header == 1);
    }

    private int deleteChildren(ScheduleSourceEpochRow terminal,int limit){
        int deleted=mapper.deleteTerminalDp08MemberStageItems(terminal.getOperationCode(),
                terminal.getEpochNo(),terminal.getVersion(),limit);requireBounded(deleted,limit);
        if(deleted>0)return deleted;
        deleted=mapper.deleteTerminalDp08MemberStageHeads(terminal.getOperationCode(),
                terminal.getEpochNo(),terminal.getVersion(),limit);requireBounded(deleted,limit);
        if(deleted>0)return deleted;
        deleted=mapper.deleteTerminalScopeRows(terminal.getOperationCode(),terminal.getEpochNo(),
                terminal.getVersion(),limit);requireBounded(deleted,limit);return deleted;
    }

    private static ScheduleSourceEpochRow requireTerminal(
            ScheduleSourceEpochRow epoch, String... states
    ) {
        ScheduleSourceEpochRow value = Objects.requireNonNull(epoch, "epoch");
        Objects.requireNonNull(value.getOperationCode(), "epoch.operationCode");
        Objects.requireNonNull(value.getEpochNo(), "epoch.epochNo");
        Objects.requireNonNull(value.getVersion(), "epoch.version");
        Objects.requireNonNull(value.getTerminalAtUtc(), "epoch.terminalAtUtc");
        for (String state : states) {
            if (state.equals(value.getEpochState())) return value;
        }
        throw new IllegalStateException("schedule epoch is not terminal");
    }

    private static void requireBounded(int changed, int maximum) {
        if (changed < 0 || changed > maximum) {
            throw new IllegalStateException("DP_SCHEDULE_RETENTION_COUNT_INVALID");
        }
    }

    private static final class RetentionWork {
        private final int childRows;
        private final boolean headerDeleted;
        private RetentionWork(int childRows, boolean headerDeleted) {
            this.childRows = childRows;
            this.headerDeleted = headerDeleted;
        }
    }
}
