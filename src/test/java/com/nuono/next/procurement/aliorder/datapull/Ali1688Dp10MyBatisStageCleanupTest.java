package com.nuono.next.procurement.aliorder.datapull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class Ali1688Dp10MyBatisStageCleanupTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 3, 4, 0);

    @Test
    void olderCleanupSelectsOnlyTheOldestExactGeneration() {
        Fixture fixture = fixture();
        when(fixture.generationCleanup.markedGeneration(
                101L, Ali1688Dp10StageCleanupReason.OLDER_GENERATION)).thenReturn(2L);
        when(fixture.generationCleanup.advance(101L, 2L,
                Ali1688Dp10StageCleanupReason.OLDER_GENERATION, 7L, NOW))
                .thenReturn(Ali1688Dp10StageCleanupAdvance.PROGRESSED);

        assertThat(fixture.cleanup.cleanupOlderGenerations(fixture.task, 4L, NOW))
                .isEqualTo(Ali1688Dp10StageCleanupAdvance.PROGRESSED);

        verify(fixture.generationCleanup).advance(101L, 2L,
                Ali1688Dp10StageCleanupReason.OLDER_GENERATION, 7L, NOW);
        verify(fixture.generationCleanup, never()).oldestGenerationBefore(101L, 4L);
        verify(fixture.generationCleanup, never()).advance(101L, 3L,
                Ali1688Dp10StageCleanupReason.OLDER_GENERATION, 7L, NOW);
    }

    @Test
    void noOlderGenerationCompletesWithoutCreatingMarker() {
        Fixture fixture = fixture();
        when(fixture.generationCleanup.markedGeneration(
                101L, Ali1688Dp10StageCleanupReason.OLDER_GENERATION)).thenReturn(null);
        when(fixture.generationCleanup.oldestGenerationBefore(101L, 4L)).thenReturn(null);

        assertThat(fixture.cleanup.cleanupOlderGenerations(fixture.task, 4L, NOW))
                .isEqualTo(Ali1688Dp10StageCleanupAdvance.COMPLETE);

        verify(fixture.generationCleanup, never()).advance(
                101L, 4L, Ali1688Dp10StageCleanupReason.OLDER_GENERATION, 7L, NOW);
    }

    @Test
    void currentCleanupUsesTheExactCheckpointGeneration() {
        Fixture fixture = fixture();
        when(fixture.generationCleanup.markedGeneration(
                101L, Ali1688Dp10StageCleanupReason.CURRENT_GENERATION)).thenReturn(null);
        when(fixture.generationCleanup.advance(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 7L, NOW))
                .thenReturn(Ali1688Dp10StageCleanupAdvance.COMPLETE);

        assertThat(fixture.cleanup.cleanupCurrentGeneration(fixture.task, 4L, NOW))
                .isEqualTo(Ali1688Dp10StageCleanupAdvance.COMPLETE);

        verify(fixture.generationCleanup).advance(101L, 4L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 7L, NOW);
    }

    @Test
    void staleLiveFenceCannotSelectOrDeleteAStageGeneration() {
        Fixture fixture = fixture();
        fixture.fence.setFenceEpoch(8L);

        assertThatThrownBy(() -> fixture.cleanup.cleanupCurrentGeneration(
                fixture.task, 1L, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DP10_TASK_FENCE_STALE");

        verify(fixture.generationCleanup, never()).oldestGenerationBefore(101L, 1L);
        verify(fixture.generationCleanup, never()).advance(101L, 1L,
                Ali1688Dp10StageCleanupReason.CURRENT_GENERATION, 7L, NOW);
    }

    private Fixture fixture() {
        Ali1688Dp10GenerationCleanup generationCleanup = mock(
                Ali1688Dp10GenerationCleanup.class);
        Ali1688Dp10RuntimeMapper runtimeMapper = mock(Ali1688Dp10RuntimeMapper.class);
        DataPullTask task = task();
        Ali1688Dp10TaskFenceRow fence = fence(task);
        when(runtimeMapper.lockTask(101L)).thenReturn(fence);
        return new Fixture(
                generationCleanup,
                new Ali1688Dp10MyBatisStageCleanup(generationCleanup, runtimeMapper),
                task,
                fence
        );
    }

    private DataPullTask task() {
        DataPullTask task = new DataPullTask();
        task.setId(101L);
        task.setOperationCode(OperationCode.DP10);
        task.setOwnerUserId(307L);
        task.setAccountKey("ali1688:member-307");
        task.setScopeKey("ali1688-owner:307:member-307");
        task.setState(TaskState.RUNNING);
        task.setLeaseOwner("worker-1");
        task.setLeaseUntil(NOW.plusMinutes(5));
        task.setFenceEpoch(7L);
        task.setVersion(11L);
        return task;
    }

    private Ali1688Dp10TaskFenceRow fence(DataPullTask task) {
        Ali1688Dp10TaskFenceRow row = new Ali1688Dp10TaskFenceRow();
        row.setId(task.getId());
        row.setOperationCode(task.getOperationCode());
        row.setOwnerUserId(task.getOwnerUserId());
        row.setAccountKey(task.getAccountKey());
        row.setScopeKey(task.getScopeKey());
        row.setState(task.getState());
        row.setLeaseOwner(task.getLeaseOwner());
        row.setLeaseUntil(task.getLeaseUntil());
        row.setFenceEpoch(task.getFenceEpoch());
        row.setVersion(task.getVersion());
        return row;
    }

    private static final class Fixture {
        private final Ali1688Dp10GenerationCleanup generationCleanup;
        private final Ali1688Dp10MyBatisStageCleanup cleanup;
        private final DataPullTask task;
        private final Ali1688Dp10TaskFenceRow fence;

        private Fixture(
                Ali1688Dp10GenerationCleanup generationCleanup,
                Ali1688Dp10MyBatisStageCleanup cleanup,
                DataPullTask task,
                Ali1688Dp10TaskFenceRow fence
        ) {
            this.generationCleanup = generationCleanup;
            this.cleanup = cleanup;
            this.task = task;
            this.fence = fence;
        }
    }
}
