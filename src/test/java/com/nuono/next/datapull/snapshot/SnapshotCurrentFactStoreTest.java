package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.SnapshotCurrentFactMapper;
import com.nuono.next.noonpull.datapull.Dp04ProductSnapshotCodec;
import com.nuono.next.noonpull.datapull.Dp04ProductSnapshotItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnapshotCurrentFactStoreTest {

    @Test
    void readsOneFullyMaterializedHeadAfterRepeatedUnsafeGenerations() {
        SnapshotCurrentFactMapper mapper = mock(SnapshotCurrentFactMapper.class);
        Dp04ProductSnapshotCodec codec = new Dp04ProductSnapshotCodec(new ObjectMapper());
        Dp04ProductSnapshotItem newest = Dp04ProductSnapshotItem.fromProvider(
                Map.of("partner_sku", "NEW", "csku_parent", "Z-NEW"), 1, 0
        );
        Dp04ProductSnapshotItem carried = Dp04ProductSnapshotItem.fromProvider(
                Map.of("partner_sku", "PRESERVED", "csku_parent", "Z-PRESERVED"),
                1, 1
        );
        SnapshotCurrentHeadRow head = head(4010L, false);
        when(mapper.selectHead(OperationCode.DP04, "scope-1")).thenReturn(head);
        when(mapper.selectCurrentChunk(OperationCode.DP04, "scope-1", 4010L, null, 20))
                .thenReturn(List.of(
                        row(4010L, 1, 0, newest, codec),
                        row(4010L, 1, 1, carried, codec)
                ));

        SnapshotCurrentFactPage<Dp04ProductSnapshotItem> page =
                new SnapshotCurrentFactStore<>(mapper, codec, codec)
                        .readChunk(OperationCode.DP04, "scope-1", null, 20)
                        .orElseThrow();

        assertThat(page.getTaskId()).isEqualTo(4010L);
        assertThat(page.isRetireMissing()).isFalse();
        assertThat(page.getItems()).extracting(item -> item.getValue().isWritableProjection())
                .containsExactly(true, true);
        assertThat(page.getItems()).extracting(SnapshotApplyItem::isAbsenceReconciliationSafe)
                .containsExactly(true, true);
        assertThat(page.getItems()).extracting(SnapshotApplyItem::isValidatedIdentityCandidate)
                .containsExactly(true, true);
    }

    @Test
    void noHeadMeansNoFactAndNeverFallsThroughToAnUnsealedStage() {
        SnapshotCurrentFactMapper mapper = mock(SnapshotCurrentFactMapper.class);
        Dp04ProductSnapshotCodec codec = new Dp04ProductSnapshotCodec(new ObjectMapper());
        when(mapper.selectHead(OperationCode.DP04, "scope-1")).thenReturn(null);

        assertThat(new SnapshotCurrentFactStore<>(mapper, codec, codec)
                .readChunk(OperationCode.DP04, "scope-1", null, 20)).isEmpty();
        verify(mapper, never()).selectCurrentChunk(
                any(), any(), anyLong(), any(), anyInt()
        );
    }

    private SnapshotStageItemRow row(
            long taskId,
            int pageNo,
            int ordinal,
            Dp04ProductSnapshotItem item,
            Dp04ProductSnapshotCodec codec
    ) {
        SnapshotStageItemRow row = new SnapshotStageItemRow();
        row.setTaskId(taskId);
        row.setPageNo(pageNo);
        row.setItemOrdinal(ordinal);
        row.setStableIdentity(codec.stableIdentity(item));
        row.setContentFingerprint(codec.stableContentFingerprint(item));
        row.setPayload(codec.encode(item));
        row.setValidatedIdentityCandidate(codec.isValidatedIdentityCandidate(item));
        row.setAbsenceReconciliationSafe(codec.isAbsenceReconciliationSafe(item));
        return row;
    }

    private SnapshotCurrentHeadRow head(long taskId, boolean retireMissing) {
        SnapshotCurrentHeadRow row = new SnapshotCurrentHeadRow();
        row.setOperationCode(OperationCode.DP04);
        row.setScopeKey("scope-1");
        row.setTaskId(taskId);
        row.setBusinessWindowKey("complete-snapshot:2026-08-03");
        row.setScheduleSlot(LocalDateTime.of(2026, 8, 3, 3, 0));
        row.setRetireMissing(retireMissing);
        row.setVersionNo(1L);
        return row;
    }
}
