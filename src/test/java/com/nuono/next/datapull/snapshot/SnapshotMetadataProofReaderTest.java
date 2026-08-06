package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.SnapshotStageProofMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SnapshotMetadataProofReaderTest {

    @Test
    void provesAccountingWithoutMaterializingPayloads() {
        SnapshotStageProofMapper mapper = mock(SnapshotStageProofMapper.class);
        SnapshotStageManifestRow manifest = manifest();
        when(mapper.selectManifest(41L)).thenReturn(manifest);
        when(mapper.countInvalidPageShapes(41L)).thenReturn(0L);
        when(mapper.countInvalidItems(41L)).thenReturn(0L);

        SnapshotStageProof<Object> proof =
                new SnapshotMetadataProofReader(mapper).prove(41L, 7L);

        assertThat(proof.isComplete()).isTrue();
        assertThat(proof.getItems()).isEmpty();
        assertThat(proof.getAppliedItemCount()).isEqualTo(300L);
        assertThat(proof.getSkippedIdentityCount()).isEqualTo(100);
        assertThat(proof.getBusinessSkippedItemCount()).isEqualTo(100L);
        assertThat(proof.getSourceItemCount()).isEqualTo(500L);
    }

    @Test
    void anyInvalidPersistedItemFailsClosed() {
        SnapshotStageProofMapper mapper = mock(SnapshotStageProofMapper.class);
        when(mapper.selectManifest(41L)).thenReturn(manifest());
        when(mapper.countInvalidPageShapes(41L)).thenReturn(0L);
        when(mapper.countInvalidItems(41L)).thenReturn(1L);

        SnapshotStageProof<Object> proof =
                new SnapshotMetadataProofReader(mapper).prove(41L, 7L);

        assertThat(proof.isComplete()).isFalse();
        assertThat(proof.getSanitizedCode()).isEqualTo("SNAPSHOT_STAGE_ITEM_INVALID");
    }

    private SnapshotStageManifestRow manifest() {
        SnapshotStageManifestRow row = new SnapshotStageManifestRow();
        row.setTaskId(41L);
        row.setActiveFenceEpoch(7L);
        row.setDeclaredTotalPages(2);
        row.setKnownLastPage(2);
        row.setAuthorityKind("PAGED_GENERATION");
        row.setAuthorityTokenSha256("a".repeat(64));
        row.setSnapshotAsOfUtc(LocalDateTime.of(2026, 8, 3, 6, 29));
        row.setDeclaredCollectionCount(500L);
        row.setPageCount(2L);
        row.setFirstPage(1);
        row.setLastPage(2);
        row.setStagedItemCount(400L);
        row.setCanonicalItemCount(300L);
        row.setSourceItemCount(500L);
        row.setBusinessSkippedItemCount(100L);
        return row;
    }
}
