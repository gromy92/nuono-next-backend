package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.Ali1688Dp10StageCleanupMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class Ali1688Dp10GenerationCleanupTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 5, 0);

    @Test
    void createsMarkerBeforeDeletingOneBoundedFingerprintBatch() {
        Fixture fixture = fixture(7);
        when(fixture.mapper.insertMarker(101L, 3L,
                Ali1688Dp10StageCleanupReason.OLDER_GENERATION, 9L, NOW)).thenReturn(1);
        when(fixture.mapper.hasFingerprintCount(101L, 3L)).thenReturn(1);
        when(fixture.mapper.deleteFingerprintCountBatch(101L, 3L, 7)).thenReturn(7);

        assertThat(fixture.cleanup.advance(101L, 3L,
                Ali1688Dp10StageCleanupReason.OLDER_GENERATION, 9L, NOW))
                .isEqualTo(Ali1688Dp10StageCleanupAdvance.PROGRESSED);

        verify(fixture.mapper).insertMarker(101L, 3L,
                Ali1688Dp10StageCleanupReason.OLDER_GENERATION, 9L, NOW);
        verify(fixture.mapper).deleteFingerprintCountBatch(101L, 3L, 7);
        verify(fixture.mapper, never()).hasIdentity(101L, 3L);
    }

    @Test
    void refreshesMatchingMarkerAndPreservesIdentityItemPageOrder() {
        Fixture fixture = fixture(5);
        when(fixture.mapper.selectMarkerReasonForUpdate(101L, 4L))
                .thenReturn(Ali1688Dp10StageCleanupReason.CURRENT_GENERATION);
        when(fixture.mapper.refreshMarker(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 9L, NOW)).thenReturn(1);
        when(fixture.mapper.hasItem(101L, 4L)).thenReturn(1);
        when(fixture.mapper.deleteItemBatch(101L, 4L, 5)).thenReturn(2);

        assertThat(fixture.cleanup.advance(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 9L, NOW))
                .isEqualTo(Ali1688Dp10StageCleanupAdvance.PROGRESSED);

        verify(fixture.mapper).hasFingerprintCount(101L, 4L);
        verify(fixture.mapper).hasIdentity(101L, 4L);
        verify(fixture.mapper).deleteItemBatch(101L, 4L, 5);
        verify(fixture.mapper, never()).hasPage(101L, 4L);
    }

    @Test
    void emptyExactGenerationDeletesOnlyItsMatchingMarker() {
        Fixture fixture = fixture(5);
        when(fixture.mapper.insertMarker(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 9L, NOW)).thenReturn(1);
        when(fixture.mapper.deleteMarker(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 9L)).thenReturn(1);

        assertThat(fixture.cleanup.advance(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 9L, NOW))
                .isEqualTo(Ali1688Dp10StageCleanupAdvance.COMPLETE);

        verify(fixture.mapper).deleteMarker(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 9L);
    }

    @Test
    void conflictingReasonOrFenceFailsBeforeAnyStageDelete() {
        Fixture fixture = fixture(5);
        when(fixture.mapper.selectMarkerReasonForUpdate(101L, 4L))
                .thenReturn(Ali1688Dp10StageCleanupReason.FAILED_RETENTION);

        assertThatThrownBy(() -> fixture.cleanup.advance(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 9L, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DP10_STAGE_CLEANUP_MARKER_CONFLICT");

        verify(fixture.mapper, never()).hasFingerprintCount(101L, 4L);
        verify(fixture.mapper, never()).deletePageBatch(101L, 4L, 5);
    }

    @Test
    void finalPageDeleteKeepsMarkerUntilTheReplayProvesGenerationEmpty() {
        Fixture fixture = fixture(5);
        when(fixture.mapper.selectMarkerReasonForUpdate(101L, 4L))
                .thenReturn(Ali1688Dp10StageCleanupReason.CURRENT_GENERATION);
        when(fixture.mapper.refreshMarker(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 9L, NOW))
                .thenReturn(1);
        when(fixture.mapper.hasPage(101L, 4L)).thenReturn(1, 0);
        when(fixture.mapper.deletePageBatch(101L, 4L, 5)).thenReturn(1);
        when(fixture.mapper.deleteMarker(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 9L)).thenReturn(1);

        assertThat(fixture.cleanup.advance(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 9L, NOW))
                .isEqualTo(Ali1688Dp10StageCleanupAdvance.PROGRESSED);
        assertThat(fixture.cleanup.advance(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 9L, NOW))
                .isEqualTo(Ali1688Dp10StageCleanupAdvance.COMPLETE);

        verify(fixture.mapper).deletePageBatch(101L, 4L, 5);
        verify(fixture.mapper).deleteMarker(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 9L);
    }

    @Test
    void durableMarkerDiscoveryRestoresTheExactGenerationAfterCrash() {
        Fixture fixture = fixture(5);
        Ali1688Dp10StageCleanupMarker marker = new Ali1688Dp10StageCleanupMarker();
        marker.setGenerationNo(3L);
        marker.setReason(Ali1688Dp10StageCleanupReason.OLDER_GENERATION);
        marker.setFenceEpoch(8L);
        when(fixture.mapper.selectTaskMarkerForUpdate(101L)).thenReturn(marker);

        assertThat(fixture.cleanup.markedGeneration(
                101L, Ali1688Dp10StageCleanupReason.OLDER_GENERATION)).isEqualTo(3L);
        assertThatThrownBy(() -> fixture.cleanup.markedGeneration(
                101L, Ali1688Dp10StageCleanupReason.CURRENT_GENERATION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DP10_STAGE_CLEANUP_MARKER_CONFLICT");
    }

    @Test
    void terminalAdoptionCasBindsOldReasonGenerationAndBothFences() {
        Fixture fixture = fixture(5);
        Ali1688Dp10StageCleanupMarker marker = new Ali1688Dp10StageCleanupMarker();
        marker.setGenerationNo(3L);
        marker.setReason(Ali1688Dp10StageCleanupReason.OLDER_GENERATION);
        marker.setFenceEpoch(8L);
        when(fixture.mapper.adoptMarkerForFailedRetention(
                101L, 3L, Ali1688Dp10StageCleanupReason.OLDER_GENERATION,
                8L, 9L, NOW)).thenReturn(1);

        fixture.cleanup.adoptForFailedRetention(101L, marker, 9L, NOW);

        verify(fixture.mapper).adoptMarkerForFailedRetention(
                101L, 3L, Ali1688Dp10StageCleanupReason.OLDER_GENERATION,
                8L, 9L, NOW);
        assertThatThrownBy(() -> fixture.cleanup.adoptForFailedRetention(
                101L, marker, 7L, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DP10_FAILED_RETENTION_MARKER_INVALID");
    }

    @Test
    void terminalAdoptionFailsClosedWhenTheMarkerCasLoses() {
        Fixture fixture = fixture(5);
        Ali1688Dp10StageCleanupMarker marker = new Ali1688Dp10StageCleanupMarker();
        marker.setGenerationNo(3L);
        marker.setReason(Ali1688Dp10StageCleanupReason.OLDER_GENERATION);
        marker.setFenceEpoch(8L);

        assertThatThrownBy(() -> fixture.cleanup.adoptForFailedRetention(
                101L, marker, 9L, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DP10_FAILED_RETENTION_MARKER_STALE");
    }

    private Fixture fixture(int batchSize) {
        Ali1688Dp10StageCleanupMapper mapper = mock(Ali1688Dp10StageCleanupMapper.class);
        Ali1688Dp10StageCleanupProperties properties = new Ali1688Dp10StageCleanupProperties();
        properties.setBatchSize(batchSize);
        return new Fixture(mapper, new Ali1688Dp10GenerationCleanup(mapper, properties));
    }

    private static final class Fixture {
        private final Ali1688Dp10StageCleanupMapper mapper;
        private final Ali1688Dp10GenerationCleanup cleanup;

        private Fixture(
                Ali1688Dp10StageCleanupMapper mapper,
                Ali1688Dp10GenerationCleanup cleanup
        ) {
            this.mapper = mapper;
            this.cleanup = cleanup;
        }
    }
}
