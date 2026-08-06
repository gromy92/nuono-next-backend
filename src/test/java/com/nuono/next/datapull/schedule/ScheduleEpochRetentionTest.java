package com.nuono.next.datapull.schedule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleEpochRetentionMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ScheduleEpochRetentionTest {

    @Test
    void abortedRetryDrainsOnlyOneBoundedChildBatch() {
        DataPullScheduleEpochRetentionMapper mapper = mock(
                DataPullScheduleEpochRetentionMapper.class
        );
        ScheduleSourceEpochRow aborted = terminal(OperationCode.DP04, 7, "ABORTED");
        when(mapper.deleteTerminalScopeRows(
                OperationCode.DP04, 7, 3, ScheduleEpochRetention.CHILD_BATCH_SIZE
        )).thenReturn(64);

        boolean pending = new ScheduleEpochRetention(mapper)
                .drainAbortedBeforeRetry(aborted);

        assertTrue(pending);
        verify(mapper, never()).deleteTerminalEpochIfEmpty(
                OperationCode.DP04, 7, 3
        );
    }

    @Test
    void restartResumesPhysicalChildProgressBeforeDeletingHeader() {
        DataPullScheduleEpochRetentionMapper mapper = mock(
                DataPullScheduleEpochRetentionMapper.class
        );
        ScheduleSourceEpochRow expired = terminal(OperationCode.DP01, 11, "COMPLETE");
        when(mapper.findExpiredTerminalEpoch(OperationCode.DP01, LocalDateTime.of(
                2026, 7, 28, 1, 0
        ))).thenReturn(expired, expired, null);
        when(mapper.deleteTerminalScopeRows(
                OperationCode.DP01, 11, 3, ScheduleEpochRetention.CHILD_BATCH_SIZE
        )).thenReturn(1, 0);
        when(mapper.deleteTerminalEpochIfEmpty(OperationCode.DP01, 11, 3)).thenReturn(1);

        new ScheduleEpochRetention(mapper, () -> 0L)
                .run(Instant.parse("2026-08-04T01:00:00Z"));

        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteTerminalScopeRows(
                OperationCode.DP01, 11, 3, ScheduleEpochRetention.CHILD_BATCH_SIZE
        );
        order.verify(mapper).deleteTerminalScopeRows(
                OperationCode.DP01, 11, 3, ScheduleEpochRetention.CHILD_BATCH_SIZE
        );
        order.verify(mapper).deleteTerminalEpochIfEmpty(OperationCode.DP01, 11, 3);
    }

    @Test
    void thirdNewestIsEligibleAndRotationVisitsTheFollowingOperation() {
        DataPullScheduleEpochRetentionMapper mapper = mock(
                DataPullScheduleEpochRetentionMapper.class
        );
        ScheduleSourceEpochRow dp01 = terminal(OperationCode.DP01, 8, "COMPLETE");
        ScheduleSourceEpochRow dp02 = terminal(OperationCode.DP02, 5, "ABORTED");
        when(mapper.findThirdNewestTerminalEpoch(OperationCode.DP01))
                .thenReturn(dp01, (ScheduleSourceEpochRow) null);
        when(mapper.findExpiredTerminalEpoch(OperationCode.DP02, LocalDateTime.of(
                2026, 7, 28, 1, 1
        ))).thenReturn(dp02, (ScheduleSourceEpochRow) null);

        ScheduleEpochRetention retention = new ScheduleEpochRetention(mapper, () -> 0L);
        retention.run(Instant.parse("2026-08-04T01:01:00Z"));

        verify(mapper, atLeastOnce()).findThirdNewestTerminalEpoch(OperationCode.DP01);
        verify(mapper, atLeastOnce()).findExpiredTerminalEpoch(
                OperationCode.DP02, LocalDateTime.of(2026, 7, 28, 1, 1)
        );
        verify(mapper).deleteTerminalEpochIfEmpty(OperationCode.DP01, 8, 3);
        verify(mapper).deleteTerminalEpochIfEmpty(OperationCode.DP02, 5, 3);
    }

    @Test
    void emptyEpochPeakCannotCreateAPermanentRetentionBacklog() {
        QueueMapper mapper = new QueueMapper();
        ScheduleEpochRetention retention = new ScheduleEpochRetention(mapper, () -> 0L);
        Instant start = Instant.parse("2026-08-04T00:00:00Z");
        long epoch = 1;

        // Every operation can seal an empty epoch every five minutes: 132 headers/hour.
        for (int tick = 0; tick < 240; tick++) {
            if (tick % 20 == 0) {
                for (OperationCode operation : OperationCode.values()) {
                    mapper.add(terminal(operation, epoch++, "COMPLETE"));
                }
            }
            retention.run(start.plusSeconds(tick * 15L));
        }

        assertTrue(mapper.isEmpty());
        assertTrue(mapper.deletedHeaders >= 132);
    }

    @Test
    void abortedRetryReportsReadyWhenNoChildrenRemain() {
        DataPullScheduleEpochRetentionMapper mapper = mock(
                DataPullScheduleEpochRetentionMapper.class
        );

        assertFalse(new ScheduleEpochRetention(mapper).drainAbortedBeforeRetry(
                terminal(OperationCode.DP07A, 9, "ABORTED")
        ));
    }

    private static ScheduleSourceEpochRow terminal(
            OperationCode operation, long epochNo, String state
    ) {
        ScheduleSourceEpochRow row = new ScheduleSourceEpochRow();
        row.setOperationCode(operation);
        row.setEpochNo(epochNo);
        row.setEpochState(state);
        row.setVersion(3L);
        row.setTerminalAtUtc(LocalDateTime.of(2026, 7, 1, 0, 0));
        return row;
    }

    private static final class QueueMapper implements DataPullScheduleEpochRetentionMapper {
        private final Map<OperationCode, ArrayDeque<ScheduleSourceEpochRow>> rows =
                new EnumMap<>(OperationCode.class);
        private int deletedHeaders;

        private void add(ScheduleSourceEpochRow row) {
            rows.computeIfAbsent(row.getOperationCode(), ignored -> new ArrayDeque<>()).add(row);
        }

        private boolean isEmpty() {
            return rows.values().stream().allMatch(ArrayDeque::isEmpty);
        }

        @Override
        public ScheduleSourceEpochRow findExpiredTerminalEpoch(
                OperationCode operation, LocalDateTime cutoff
        ) {
            ArrayDeque<ScheduleSourceEpochRow> queue = rows.get(operation);
            return queue == null ? null : queue.peekFirst();
        }

        @Override
        public ScheduleSourceEpochRow findThirdNewestTerminalEpoch(OperationCode operation) {
            return null;
        }

        @Override
        public int deleteTerminalDp08MemberStageItems(
                OperationCode operation,long epochNo,long version,int limit
        ){return 0;}

        @Override
        public int deleteTerminalDp08MemberStageHeads(
                OperationCode operation,long epochNo,long version,int limit
        ){return 0;}

        @Override
        public int deleteTerminalScopeRows(
                OperationCode operation, long epochNo, long version, int limit
        ) {
            return 0;
        }

        @Override
        public int deleteTerminalEpochIfEmpty(
                OperationCode operation, long epochNo, long version
        ) {
            ScheduleSourceEpochRow removed = rows.get(operation).removeFirst();
            if (!removed.getEpochNo().equals(epochNo)) throw new AssertionError("epoch drift");
            deletedHeaders++;
            return 1;
        }
    }
}
