package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.infrastructure.mapper.SnapshotCarryProgressMapper;
import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SnapshotCarryFenceTest {
    private static final Instant NOW = Instant.parse("2026-08-03T03:01:00Z");
    private static final SnapshotPayloadCodec<String> CODEC = new SnapshotPayloadCodec<>() {
        @Override public String encode(String item) { return item; }
        @Override public String decode(String payload) { return payload; }
    };

    @Test
    void startsFullCarryAgainstTheLockedHeadVersion() {
        SnapshotFactApplyMapper mapper = mock(SnapshotFactApplyMapper.class);
        SnapshotCarryProgressMapper carry = mock(SnapshotCarryProgressMapper.class);
        CompleteSnapshot<String> snapshot = snapshot();
        SnapshotApplyProgressRow progress = progress(snapshot, "PREPARING", 0L, null, null);
        SnapshotCurrentHeadRow source = head(snapshot, 4001L, 3L);
        when(mapper.selectTaskForUpdate(snapshot.getTaskId())).thenReturn(liveTask(snapshot));
        when(mapper.selectProgressForUpdate(snapshot.getTaskId())).thenReturn(progress);
        when(mapper.selectCanonicalChunk(snapshot.getTaskId(), 1, 0, 20))
                .thenReturn(List.of());
        when(mapper.selectCurrentHeadForUpdate(snapshot)).thenReturn(source);
        when(carry.startCarry(snapshot, SnapshotCarryMode.FULL, 4001L, 3L, nowUtc()))
                .thenReturn(1);

        CompleteSnapshotWriter.ReplaceResult result = guard(mapper, carry).advance(
                snapshot, descriptor(), CODEC, List::size,
                (task, mode, cursor, limit) -> SnapshotCarryForwardResult.complete(),
                ignored -> { }
        );

        assertThat(result).isEqualTo(CompleteSnapshotWriter.ReplaceResult.MORE_WORK);
        verify(carry).startCarry(snapshot, SnapshotCarryMode.FULL, 4001L, 3L, nowUtc());
        verify(mapper, never()).upsertCurrentHead(any(), anyBoolean(), any());
    }

    @Test
    void changedHeadVersionStopsBeforeReadingOrPublishingCarryTarget() {
        SnapshotFactApplyMapper mapper = mock(SnapshotFactApplyMapper.class);
        SnapshotCarryProgressMapper carry = mock(SnapshotCarryProgressMapper.class);
        CompleteSnapshot<String> snapshot = snapshot();
        SnapshotApplyProgressRow progress = progress(
                snapshot, "CARRYING", 1L, 4001L, 3L
        );
        SnapshotCurrentHeadRow changed = head(snapshot, 4001L, 4L);
        when(mapper.selectTaskForUpdate(snapshot.getTaskId())).thenReturn(liveTask(snapshot));
        when(mapper.selectProgressForUpdate(snapshot.getTaskId())).thenReturn(progress);
        when(mapper.selectCurrentHeadForUpdate(snapshot)).thenReturn(changed);
        AtomicBoolean readCarry = new AtomicBoolean();

        CompleteSnapshotWriter.ReplaceResult result = guard(mapper, carry).advance(
                snapshot, descriptor(), CODEC, List::size,
                (task, mode, cursor, limit) -> {
                    readCarry.set(true);
                    return SnapshotCarryForwardResult.complete();
                },
                ignored -> { }
        );

        assertThat(result).isEqualTo(CompleteSnapshotWriter.ReplaceResult.STALE_FENCE);
        assertThat(readCarry).isFalse();
        verify(mapper, never()).upsertCurrentHead(any(), anyBoolean(), any());
    }

    private SnapshotFactApplyGuard guard(
            SnapshotFactApplyMapper mapper,
            SnapshotCarryProgressMapper carry
    ) {
        return new SnapshotFactApplyGuard(
                mapper, carry, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private SnapshotItemDescriptor<String> descriptor() {
        return new SnapshotItemDescriptor<>() {
            @Override public String stableIdentity(String item) { return item; }
            @Override public String stableContentFingerprint(String item) {
                return "a".repeat(64);
            }
            @Override public boolean isAbsenceReconciliationSafe(String item) { return false; }
        };
    }

    private SnapshotApplyProgressRow progress(
            CompleteSnapshot<?> snapshot,
            String state,
            long effective,
            Long sourceTaskId,
            Long sourceVersion
    ) {
        SnapshotApplyProgressRow row = new SnapshotApplyProgressRow();
        row.setTaskId(snapshot.getTaskId());
        row.setActiveFenceEpoch(snapshot.getFenceEpoch());
        row.setCursorPageNo(1);
        row.setCursorItemOrdinal(0);
        row.setPreparedItemCount(1L);
        row.setAbsenceUnsafeItemCount(1L);
        row.setEffectiveItemCount(effective);
        row.setCarryMode("CARRYING".equals(state) ? SnapshotCarryMode.FULL : SnapshotCarryMode.NONE);
        row.setCarrySourceTaskId(sourceTaskId);
        row.setCarrySourceHeadVersion(sourceVersion);
        row.setState(state);
        return row;
    }

    private SnapshotCurrentHeadRow head(
            CompleteSnapshot<?> snapshot,
            long taskId,
            long version
    ) {
        SnapshotCurrentHeadRow row = new SnapshotCurrentHeadRow();
        row.setOperationCode(snapshot.getOperationCode());
        row.setScopeKey(snapshot.getScopeKey());
        row.setTaskId(taskId);
        row.setBusinessWindowKey("snapshot:old");
        row.setScheduleSlot(LocalDateTime.of(2026, 8, 2, 3, 0));
        row.setRetireMissing(false);
        row.setVersionNo(version);
        return row;
    }

    private SnapshotApplyTaskRow liveTask(CompleteSnapshot<?> snapshot) {
        SnapshotApplyTaskRow row = new SnapshotApplyTaskRow();
        row.setTaskId(snapshot.getTaskId());
        row.setOperationCode(snapshot.getOperationCode());
        row.setScopeKey(snapshot.getScopeKey());
        row.setBusinessWindowKey(snapshot.getBusinessWindowKey());
        row.setFenceEpoch(snapshot.getFenceEpoch());
        row.setState("RUNNING");
        row.setLeaseOwner(snapshot.getLeaseOwner());
        row.setLeaseUntil(LocalDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC));
        return row;
    }

    private CompleteSnapshot<String> snapshot() {
        DataPullTask task = DataPullTask.queued(
                4002L, OperationCode.DP04, "NOON_PARTNER_PRODUCT_LIST", 307L, 8001L,
                "PRJ108065", null, "PRJ108065", "STR108065-NSA", "SA", "scope-1",
                LocalDateTime.of(2026, 8, 3, 3, 0), "snapshot:2026-08-03",
                "SNAPSHOT_APPLY", LocalDateTime.of(2026, 8, 3, 3, 0)
        );
        task.setState(TaskState.RUNNING);
        task.setFenceEpoch(7L);
        task.setLeaseOwner("worker");
        task.setLeaseUntil(LocalDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC));
        SnapshotCollectionAuthority authority = SnapshotCollectionAuthority.fromProviderToken(
                SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                "generation-current", LocalDateTime.of(2026, 8, 3, 3, 0), 1L
        );
        return CompleteSnapshot.from(task, SnapshotStageProof.completeMetadata(
                1, 1L, 0, 0L, 1L, authority
        ));
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}
