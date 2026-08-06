package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.checkpoint.DataPullScopeProgress;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10FailedStageRetentionMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class Ali1688Dp10FailedStageRetentionCleanerTest {
    private static final Instant NOW = Instant.parse("2026-08-03T04:00:00Z");
    private static final LocalDateTime CUTOFF = LocalDateTime.parse("2026-07-27T04:00:00");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.parse("2026-08-03T04:00:00");

    @Test
    void currentGenerationOrphanIsAdoptedAfterTaskThenMarkerLocks() {
        Fixture fixture = fixture();
        Ali1688Dp10FailedStageCandidate candidate = candidate(101L, 4L, true);
        Ali1688Dp10FailedTaskFence task = taskFence(
                Ali1688Dp10Job.CLEANUP_STEP, checkpoint(4L), 12L);
        Ali1688Dp10StageCleanupMarker marker = marker(
                4L, Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 11L);
        when(fixture.mapper.selectOldestEligibleMarker(CUTOFF)).thenReturn(candidate);
        when(fixture.mapper.lockEligibleTaskFence(101L, CUTOFF)).thenReturn(task);
        when(fixture.cleanup.lockTaskMarker(101L)).thenReturn(marker);

        fixture.cleaner.run(NOW);

        InOrder lockOrder = inOrder(fixture.mapper, fixture.cleanup);
        lockOrder.verify(fixture.mapper).lockEligibleTaskFence(101L, CUTOFF);
        lockOrder.verify(fixture.cleanup).lockTaskMarker(101L);
        verify(fixture.cleanup).adoptForFailedRetention(101L, marker, 12L, NOW_LOCAL);
        verify(fixture.cleanup).advance(101L, 4L,
                Ali1688Dp10StageCleanupReason.FAILED_RETENTION, 12L, NOW_LOCAL);
    }

    @Test
    void olderGenerationOrphanIsAdoptedBeforeStageCandidateDiscovery() {
        Fixture fixture = fixture();
        Ali1688Dp10FailedStageCandidate candidate = candidate(101L, 3L, true);
        Ali1688Dp10FailedTaskFence task = taskFence(
                Ali1688Dp10Job.LIST_STEP, checkpoint(4L), 12L);
        Ali1688Dp10StageCleanupMarker marker = marker(
                3L, Ali1688Dp10StageCleanupReason.OLDER_GENERATION, 10L);
        when(fixture.mapper.selectOldestEligibleMarker(CUTOFF)).thenReturn(candidate);
        when(fixture.mapper.lockEligibleTaskFence(101L, CUTOFF)).thenReturn(task);
        when(fixture.cleanup.lockTaskMarker(101L)).thenReturn(marker);

        fixture.cleaner.run(NOW);

        verify(fixture.mapper, never()).selectOldestEligibleGeneration(CUTOFF);
        verify(fixture.cleanup).adoptForFailedRetention(101L, marker, 12L, NOW_LOCAL);
        verify(fixture.cleanup).advance(101L, 3L,
                Ali1688Dp10StageCleanupReason.FAILED_RETENTION, 12L, NOW_LOCAL);
    }

    @Test
    void noMarkerCreatesFailedRetentionForOnlyTheSelectedStageGeneration() {
        Fixture fixture = fixture();
        when(fixture.mapper.selectOldestEligibleGeneration(CUTOFF))
                .thenReturn(candidate(101L, 2L, false));
        when(fixture.mapper.lockEligibleTaskFence(101L, CUTOFF))
                .thenReturn(taskFence(Ali1688Dp10Job.LIST_STEP, null, 12L));
        when(fixture.cleanup.lockTaskMarker(101L)).thenReturn(null);

        fixture.cleaner.run(NOW);

        verify(fixture.cleanup, never()).adoptForFailedRetention(
                101L, null, 12L, NOW_LOCAL);
        verify(fixture.cleanup).advance(101L, 2L,
                Ali1688Dp10StageCleanupReason.FAILED_RETENTION, 12L, NOW_LOCAL);
    }

    @Test
    void wrongGenerationAndForwardFenceFailClosed() {
        assertInvalidMarker(marker(
                3L, Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 11L),
                taskFence(Ali1688Dp10Job.CLEANUP_STEP, checkpoint(4L), 12L));
        assertInvalidMarker(marker(
                4L, Ali1688Dp10StageCleanupReason.OLDER_GENERATION, 11L),
                taskFence(Ali1688Dp10Job.LIST_STEP, checkpoint(4L), 12L));
        assertInvalidMarker(marker(
                4L, Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 13L),
                taskFence(Ali1688Dp10Job.CLEANUP_STEP, checkpoint(4L), 12L));
    }

    @Test
    void taskMustRemainEligibleWhenLocked() {
        Fixture fixture = fixture();
        when(fixture.mapper.selectOldestEligibleMarker(CUTOFF))
                .thenReturn(candidate(101L, 4L, true));
        when(fixture.mapper.lockEligibleTaskFence(101L, CUTOFF)).thenReturn(null);

        assertThatThrownBy(() -> fixture.cleaner.run(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DP10_FAILED_RETENTION_TASK_STALE");

        verify(fixture.cleanup, never()).lockTaskMarker(101L);
    }

    private void assertInvalidMarker(
            Ali1688Dp10StageCleanupMarker marker,
            Ali1688Dp10FailedTaskFence task
    ) {
        Fixture fixture = fixture();
        when(fixture.mapper.selectOldestEligibleMarker(CUTOFF))
                .thenReturn(candidate(101L, marker.getGenerationNo(), true));
        when(fixture.mapper.lockEligibleTaskFence(101L, CUTOFF)).thenReturn(task);
        when(fixture.cleanup.lockTaskMarker(101L)).thenReturn(marker);

        assertThatThrownBy(() -> fixture.cleaner.run(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DP10_FAILED_RETENTION_MARKER_INVALID");

        verify(fixture.cleanup, never()).adoptForFailedRetention(
                101L, marker, 12L, NOW_LOCAL);
    }

    private Fixture fixture() {
        Ali1688Dp10FailedStageRetentionMapper mapper = mock(
                Ali1688Dp10FailedStageRetentionMapper.class);
        Ali1688Dp10GenerationCleanup cleanup = mock(Ali1688Dp10GenerationCleanup.class);
        Ali1688Dp10StageCleanupProperties properties = new Ali1688Dp10StageCleanupProperties();
        properties.setBatchSize(2);
        properties.setRetentionRunIntervalSeconds(3_600L);
        return new Fixture(mapper, cleanup, new Ali1688Dp10FailedStageRetentionCleaner(
                mapper, cleanup, properties, new ObjectMapper()));
    }

    private Ali1688Dp10FailedStageCandidate candidate(
            long taskId, long generationNo, boolean markerCandidate
    ) {
        Ali1688Dp10FailedStageCandidate candidate = new Ali1688Dp10FailedStageCandidate();
        candidate.setTaskId(taskId);
        candidate.setGenerationNo(generationNo);
        candidate.setMarkerCandidate(markerCandidate);
        return candidate;
    }

    private Ali1688Dp10FailedTaskFence taskFence(
            String step, String checkpoint, long fenceEpoch
    ) {
        Ali1688Dp10FailedTaskFence task = new Ali1688Dp10FailedTaskFence();
        task.setTaskId(101L);
        task.setFenceEpoch(fenceEpoch);
        task.setStepCode(step);
        task.setCheckpoint(checkpoint);
        return task;
    }

    private Ali1688Dp10StageCleanupMarker marker(
            long generationNo,
            Ali1688Dp10StageCleanupReason reason,
            long fenceEpoch
    ) {
        Ali1688Dp10StageCleanupMarker marker = new Ali1688Dp10StageCleanupMarker();
        marker.setGenerationNo(generationNo);
        marker.setReason(reason);
        marker.setFenceEpoch(fenceEpoch);
        return marker;
    }

    private String checkpoint(long generationNo) {
        LocalDateTime now = LocalDateTime.parse("2026-08-03T04:00:00");
        DataPullScopeProgress progress = DataPullScopeProgress.initial(
                OperationCode.DP10, "ali1688-owner:307:member-307", now);
        Ali1688Dp10Checkpoint checkpoint = Ali1688Dp10Checkpoint.initial(
                progress, now, 100);
        checkpoint.setGenerationNo(generationNo);
        return new Ali1688Dp10CheckpointCodec(new ObjectMapper()).encode(checkpoint);
    }

    private static final class Fixture {
        private final Ali1688Dp10FailedStageRetentionMapper mapper;
        private final Ali1688Dp10GenerationCleanup cleanup;
        private final Ali1688Dp10FailedStageRetentionCleaner cleaner;

        private Fixture(
                Ali1688Dp10FailedStageRetentionMapper mapper,
                Ali1688Dp10GenerationCleanup cleanup,
                Ali1688Dp10FailedStageRetentionCleaner cleaner
        ) {
            this.mapper = mapper;
            this.cleanup = cleanup;
            this.cleaner = cleaner;
        }
    }
}
