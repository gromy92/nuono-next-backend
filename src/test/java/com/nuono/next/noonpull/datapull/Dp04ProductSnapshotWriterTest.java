package com.nuono.next.noonpull.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.snapshot.CompleteSnapshot;
import com.nuono.next.datapull.snapshot.CompleteSnapshotWriter;
import com.nuono.next.datapull.snapshot.SnapshotCollectionAuthority;
import com.nuono.next.datapull.snapshot.SnapshotFactApplyGuard;
import com.nuono.next.datapull.snapshot.SnapshotEffectiveItemStore;
import com.nuono.next.datapull.snapshot.SnapshotStageProof;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class Dp04ProductSnapshotWriterTest {

    @Test
    void delegatesOnlyToTheDpOwnedBoundedSnapshotGuard() {
        SnapshotFactApplyGuard guard = mock(SnapshotFactApplyGuard.class);
        Dp04ProductSnapshotCodec codec = new Dp04ProductSnapshotCodec(new ObjectMapper());
        @SuppressWarnings("unchecked")
        SnapshotEffectiveItemStore<Dp04ProductSnapshotItem> effective =
                mock(SnapshotEffectiveItemStore.class);
        CompleteSnapshot<Dp04ProductSnapshotItem> snapshot = snapshot(OperationCode.DP04);
        when(guard.advance(eq(snapshot), eq(codec), eq(codec), any(), any(), any()))
                .thenReturn(CompleteSnapshotWriter.ReplaceResult.MORE_WORK);

        CompleteSnapshotWriter.ReplaceResult result =
                new Dp04ProductSnapshotWriter(guard, codec, effective).replace(snapshot);

        assertThat(result).isEqualTo(CompleteSnapshotWriter.ReplaceResult.MORE_WORK);
        verify(guard).advance(eq(snapshot), eq(codec), eq(codec), any(), any(), any());
    }

    @Test
    void rejectsAnyNonDp04ScopeBeforeTheGuard() {
        SnapshotFactApplyGuard guard = mock(SnapshotFactApplyGuard.class);
        Dp04ProductSnapshotCodec codec = new Dp04ProductSnapshotCodec(new ObjectMapper());
        @SuppressWarnings("unchecked")
        SnapshotEffectiveItemStore<Dp04ProductSnapshotItem> effective =
                mock(SnapshotEffectiveItemStore.class);

        assertThatThrownBy(() -> new Dp04ProductSnapshotWriter(guard, codec, effective)
                .replace(snapshot(OperationCode.DP07A)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DP-04 complete snapshot scope is invalid");
    }

    private CompleteSnapshot<Dp04ProductSnapshotItem> snapshot(OperationCode operationCode) {
        DataPullTask task = DataPullTask.queued(
                4001L, operationCode, "NOON_PARTNER_PRODUCT_LIST", 307L, 8001L,
                "PRJ108065", null, "PRJ108065", "STR108065-NSA", "SA", "scope-1",
                LocalDateTime.of(2026, 8, 2, 3, 0), "complete-snapshot:2026-08-02",
                "SNAPSHOT_APPLY", LocalDateTime.of(2026, 8, 2, 3, 0)
        );
        task.setFenceEpoch(1L);
        task.setLeaseOwner("snapshot-test-worker");
        task.setLeaseUntil(LocalDateTime.of(2026, 8, 2, 3, 5));
        SnapshotCollectionAuthority authority = SnapshotCollectionAuthority.fromProviderToken(
                SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                "dp04-writer-test-generation", LocalDateTime.of(2026, 8, 2, 3, 0), 1L
        );
        return CompleteSnapshot.from(task, SnapshotStageProof.completeMetadata(
                1, 1L, 0, 0L, 1L, authority
        ));
    }
}
