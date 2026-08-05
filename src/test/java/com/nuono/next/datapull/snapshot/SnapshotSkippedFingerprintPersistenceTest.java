package com.nuono.next.datapull.snapshot;

import static com.nuono.next.datapull.snapshot.MyBatisSnapshotStageFixture.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.SnapshotTwoPassMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SnapshotSkippedFingerprintPersistenceTest {
    @Test
    void passOnePersistsEachSkippedFingerprintWithItsMultiplicity() {
        FakeCompleteSnapshotStageMapper stageMapper = new FakeCompleteSnapshotStageMapper();
        stageMapper.task(803L, 1L, "RUNNING");
        SnapshotTwoPassMapper twoPassMapper = mock(SnapshotTwoPassMapper.class);
        when(twoPassMapper.updateMetadataAndMode(
                anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);
        when(twoPassMapper.upsertPassOneCounts(anyLong(), any())).thenAnswer(call ->
                ((List<?>) call.getArgument(1)).size()
        );
        when(twoPassMapper.recordPassOnePage(anyLong(), anyLong(), anyInt())).thenReturn(1);
        MyBatisSnapshotStageStore<MyBatisSnapshotStageFixture.Item> store =
                MyBatisSnapshotStageFixture.store(stageMapper, twoPassMapper);
        String duplicateSkipped = "a".repeat(64);
        String distinctSkipped = "b".repeat(64);

        store.stagePage(803L, 1L, SnapshotPage.twoPassRequired(
                1, null, true, 1, List.of(item("A", "one")), 4, 3,
                List.of(duplicateSkipped, duplicateSkipped, distinctSkipped)
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SnapshotFingerprintCountRow>> rows =
                ArgumentCaptor.forClass(List.class);
        verify(twoPassMapper).upsertPassOneCounts(anyLong(), rows.capture());
        assertThat(rows.getValue()).anySatisfy(row -> {
            assertThat(row.getContentFingerprint()).isEqualTo(duplicateSkipped);
            assertThat(row.getPassOneCount()).isEqualTo(2L);
        }).anySatisfy(row -> {
            assertThat(row.getContentFingerprint()).isEqualTo(distinctSkipped);
            assertThat(row.getPassOneCount()).isEqualTo(1L);
        });
    }
}
