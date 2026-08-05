package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import com.nuono.next.infrastructure.mapper.SnapshotCarryProgressMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.Test;

class SnapshotFactApplyGuardTest {
    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");
    private static final String FINGERPRINT = "a".repeat(64);
    private static final SnapshotPayloadCodec<String> CODEC = new SnapshotPayloadCodec<>() {
        @Override public String encode(String item) { return item; }
        @Override public String decode(String payload) { return payload; }
    };

    @Test
    void preparesOneBoundedChunkWithoutMovingTheCurrentHead() {
        SnapshotFactApplyMapper mapper = mock(SnapshotFactApplyMapper.class);
        CompleteSnapshot<String> snapshot = snapshot("snapshot-worker-a");
        SnapshotApplyProgressRow progress = progress(snapshot, 0, -1, 0L, 0L);
        SnapshotStageItemRow row = staged(snapshot, true);
        when(mapper.selectTaskForUpdate(snapshot.getTaskId())).thenReturn(liveTask(snapshot));
        when(mapper.insertProgressIfAbsent(eq(snapshot), any(LocalDateTime.class))).thenReturn(1);
        when(mapper.selectProgressForUpdate(snapshot.getTaskId())).thenReturn(progress);
        when(mapper.selectCanonicalChunk(snapshot.getTaskId(), 0, -1, 20))
                .thenReturn(List.of(row));
        when(mapper.advanceProgress(
                eq(snapshot), eq(0), eq(-1), eq(1), eq(0), eq(1), eq(0), eq(1), any()
        )).thenReturn(1);
        AtomicReference<List<SnapshotApplyItem<String>>> prepared = new AtomicReference<>();
        LongConsumer domainSeal = mock(LongConsumer.class);

        CompleteSnapshotWriter.ReplaceResult result = guard(mapper).advance(
                snapshot, descriptor(true), CODEC,
                items -> { prepared.set(items); return items.size(); },
                (source, mode, cursor, limit) -> SnapshotCarryForwardResult.complete(),
                domainSeal
        );

        assertThat(result).isEqualTo(CompleteSnapshotWriter.ReplaceResult.MORE_WORK);
        assertThat(prepared.get()).singleElement().satisfies(item -> {
            assertThat(item.getValue()).isEqualTo("fact");
            assertThat(item.isValidatedIdentityCandidate()).isTrue();
            assertThat(item.isAbsenceReconciliationSafe()).isTrue();
        });
        verify(mapper, never()).selectCurrentHeadForUpdate(any());
        verify(mapper, never()).upsertCurrentHead(any(), anyBoolean(), any());
        verify(mapper, never()).insertMarkerIfLive(any(), anyLong(), any(), any(), any());
        verify(mapper, never()).markProgressSealed(
                anyLong(), anyLong(), anyLong(), anyLong(), any(), any()
        );
        verify(domainSeal, never()).accept(anyLong());
    }

    @Test
    void concurrentNewerHeadWinsWithoutRunningDomainSeal() {
        SnapshotFactApplyMapper mapper = mock(SnapshotFactApplyMapper.class);
        CompleteSnapshot<String> snapshot = snapshot("snapshot-worker-a");
        SnapshotApplyProgressRow progress = progress(snapshot, 1, 0, 1L, 0L);
        SnapshotCurrentHeadRow winner = head(
                snapshot, 5000L, LocalDateTime.of(2026, 8, 4, 3, 0)
        );
        when(mapper.selectTaskForUpdate(snapshot.getTaskId())).thenReturn(liveTask(snapshot));
        when(mapper.selectProgressForUpdate(snapshot.getTaskId())).thenReturn(progress);
        when(mapper.selectCanonicalChunk(snapshot.getTaskId(), 1, 0, 20)).thenReturn(List.of());
        when(mapper.selectCurrentHeadForUpdate(snapshot)).thenReturn(null, winner);
        when(mapper.upsertCurrentHead(eq(snapshot), eq(true), any())).thenReturn(0);
        LongConsumer domainSeal = mock(LongConsumer.class);

        CompleteSnapshotWriter.ReplaceResult result = guard(mapper).advance(
                snapshot, descriptor(true), CODEC, List::size,
                (source, mode, cursor, limit) -> SnapshotCarryForwardResult.complete(),
                domainSeal
        );

        assertThat(result).isEqualTo(CompleteSnapshotWriter.ReplaceResult.STALE_FENCE);
        verify(domainSeal, never()).accept(anyLong());
        verify(mapper, never()).insertMarkerIfLive(any(), anyLong(), any(), any(), any());
        verify(mapper, never()).markProgressSealed(
                anyLong(), anyLong(), anyLong(), anyLong(), any(), any()
        );
    }

    @Test
    void priorMarkerMustMatchTheExactAuthoritativeContainer() {
        SnapshotFactApplyMapper mapper = mock(SnapshotFactApplyMapper.class);
        CompleteSnapshot<String> snapshot = snapshot("snapshot-worker-a");
        SnapshotApplyMarkerRow marker = marker(snapshot);
        marker.setAuthorityTokenSha256("f".repeat(64));
        when(mapper.selectTaskForUpdate(snapshot.getTaskId())).thenReturn(liveTask(snapshot));
        when(mapper.selectMarker(snapshot.getTaskId())).thenReturn(marker);

        assertThatThrownBy(() -> guard(mapper).advance(
                snapshot, descriptor(true), CODEC, List::size,
                (source, mode, cursor, limit) -> SnapshotCarryForwardResult.complete(),
                ignored -> { }
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("snapshot fact marker identity drift");
        verify(mapper, never()).insertProgressIfAbsent(any(), any());
    }

    @Test
    void taskFenceOwnerAndLeaseMustRemainCurrentBeforePreparation() {
        assertStale(row -> row.setTaskId(4002L));
        assertStale(row -> row.setFenceEpoch(8L));
        assertStale(row -> row.setLeaseOwner("snapshot-worker-b"));
        assertStale(row -> row.setLeaseUntil(nowUtc()));
    }

    private void assertStale(Consumer<SnapshotApplyTaskRow> mutateTask) {
        SnapshotFactApplyMapper mapper = mock(SnapshotFactApplyMapper.class);
        CompleteSnapshot<String> snapshot = snapshot("snapshot-worker-a");
        SnapshotApplyTaskRow current = liveTask(snapshot);
        mutateTask.accept(current);
        when(mapper.selectTaskForUpdate(snapshot.getTaskId())).thenReturn(current);

        CompleteSnapshotWriter.ReplaceResult result = guard(mapper).advance(
                snapshot, descriptor(true), CODEC, List::size,
                (source, mode, cursor, limit) -> SnapshotCarryForwardResult.complete(),
                ignored -> { }
        );

        assertThat(result).isEqualTo(CompleteSnapshotWriter.ReplaceResult.STALE_FENCE);
        verify(mapper, never()).selectMarker(snapshot.getTaskId());
        verify(mapper, never()).insertProgressIfAbsent(any(), any());
    }

    private SnapshotItemDescriptor<String> descriptor(boolean absenceSafe) {
        return new SnapshotItemDescriptor<>() {
            @Override public String stableIdentity(String item) { return item; }
            @Override public String stableContentFingerprint(String item) { return FINGERPRINT; }
            @Override public boolean isAbsenceReconciliationSafe(String item) {
                return absenceSafe;
            }
        };
    }

    private SnapshotFactApplyGuard guard(SnapshotFactApplyMapper mapper) {
        return new SnapshotFactApplyGuard(
                mapper,
                mock(SnapshotCarryProgressMapper.class),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private CompleteSnapshot<String> snapshot(String leaseOwner) {
        DataPullTask task = DataPullTask.queued(
                4001L, OperationCode.DP04, "NOON_PARTNER_PRODUCT_LIST", 307L, 8001L,
                "PRJ108065", null, "PRJ108065", "STR108065-NSA", "SA", "scope-1",
                LocalDateTime.of(2026, 8, 3, 3, 0), "complete-snapshot:2026-08-03",
                "SNAPSHOT_APPLY", LocalDateTime.of(2026, 8, 3, 3, 0)
        );
        task.setState(TaskState.RUNNING);
        task.setFenceEpoch(7L);
        task.setLeaseOwner(leaseOwner);
        task.setLeaseUntil(LocalDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC));
        SnapshotCollectionAuthority authority = SnapshotCollectionAuthority.fromProviderToken(
                SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                "generation-1", LocalDateTime.of(2026, 8, 3, 0, 0), 1L
        );
        return CompleteSnapshot.from(task, SnapshotStageProof.completeMetadata(
                1, 1L, 0, 0L, 1L, authority
        ));
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

    private SnapshotApplyProgressRow progress(
            CompleteSnapshot<?> snapshot,
            int pageNo,
            int itemOrdinal,
            long prepared,
            long absenceUnsafe
    ) {
        SnapshotApplyProgressRow row = new SnapshotApplyProgressRow();
        row.setTaskId(snapshot.getTaskId());
        row.setActiveFenceEpoch(snapshot.getFenceEpoch());
        row.setCursorPageNo(pageNo);
        row.setCursorItemOrdinal(itemOrdinal);
        row.setPreparedItemCount(prepared);
        row.setAbsenceUnsafeItemCount(absenceUnsafe);
        row.setEffectiveItemCount(prepared);
        row.setCarryMode(SnapshotCarryMode.NONE);
        row.setState("PREPARING");
        return row;
    }

    private SnapshotStageItemRow staged(CompleteSnapshot<?> snapshot, boolean absenceSafe) {
        SnapshotStageItemRow row = new SnapshotStageItemRow();
        row.setTaskId(snapshot.getTaskId());
        row.setPageNo(1);
        row.setItemOrdinal(0);
        row.setStableIdentity("fact");
        row.setContentFingerprint(FINGERPRINT);
        row.setPayload("fact");
        row.setValidatedIdentityCandidate(true);
        row.setAbsenceReconciliationSafe(absenceSafe);
        return row;
    }

    private SnapshotCurrentHeadRow head(
            CompleteSnapshot<?> snapshot,
            long taskId,
            LocalDateTime scheduleSlot
    ) {
        SnapshotCurrentHeadRow row = new SnapshotCurrentHeadRow();
        row.setOperationCode(snapshot.getOperationCode());
        row.setScopeKey(snapshot.getScopeKey());
        row.setTaskId(taskId);
        row.setBusinessWindowKey(taskId == snapshot.getTaskId()
                ? snapshot.getBusinessWindowKey() : "window-" + taskId);
        row.setScheduleSlot(scheduleSlot);
        row.setRetireMissing(true);
        row.setVersionNo(1L);
        return row;
    }

    private SnapshotApplyMarkerRow marker(CompleteSnapshot<?> snapshot) {
        SnapshotApplyMarkerRow row = new SnapshotApplyMarkerRow();
        row.setTaskId(snapshot.getTaskId());
        row.setOperationCode(snapshot.getOperationCode());
        row.setScopeKey(snapshot.getScopeKey());
        row.setBusinessWindowKey(snapshot.getBusinessWindowKey());
        row.setAppliedFenceEpoch(snapshot.getFenceEpoch());
        row.setAuthorityKind(snapshot.getAuthority().getKind());
        row.setAuthorityTokenSha256(snapshot.getAuthority().getGenerationTokenSha256());
        row.setSnapshotAsOfUtc(snapshot.getAuthority().getProviderAsOfUtc());
        row.setDeclaredCollectionCount(snapshot.getAuthority().getDeclaredCollectionCount());
        row.setSourceItemCount(snapshot.getSourceItemCount());
        row.setAppliedItemCount(snapshot.getAppliedItemCount());
        row.setIdentitySkippedItemCount((long) snapshot.getSkippedIdentityCount());
        row.setBusinessSkippedItemCount(snapshot.getBusinessSkippedItemCount());
        row.setLastPage(snapshot.getLastPage());
        row.setEffectiveItemCount(snapshot.getAppliedItemCount());
        row.setCarryMode(SnapshotCarryMode.NONE);
        return row;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}
