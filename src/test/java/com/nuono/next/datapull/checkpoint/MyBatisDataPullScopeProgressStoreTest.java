package com.nuono.next.datapull.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.infrastructure.mapper.DataPullScopeProgressMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MyBatisDataPullScopeProgressStoreTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 4, 0);

    @Test
    void idempotentInitializationReturnsThePersistedProgress() {
        DataPullScopeProgressMapper mapper = mock(DataPullScopeProgressMapper.class);
        DataPullScopeProgress stored = DataPullScopeProgress.initial(OperationCode.DP10, "scope-10", NOW);
        when(mapper.insertIfAbsent(any())).thenReturn(0);
        when(mapper.select(OperationCode.DP10, "scope-10")).thenReturn(stored);

        DataPullScopeProgress resolved = new MyBatisDataPullScopeProgressStore(mapper)
                .getOrCreate(OperationCode.DP10, "scope-10", NOW);

        assertEquals(0L, resolved.getVersion());
        assertFalse(resolved.isInitialFullCompleted());
    }

    @Test
    void staleFenceOrProgressVersionIsReportedWithoutPretendingToCommit() {
        DataPullScopeProgressMapper mapper = mock(DataPullScopeProgressMapper.class);
        when(mapper.commitCompletedWindow(any())).thenReturn(0);
        MyBatisDataPullScopeProgressStore store = new MyBatisDataPullScopeProgressStore(mapper);

        assertFalse(store.commitCompletedWindow(new DataPullScopeProgressCommit(
                claimedTask(),
                3L,
                true,
                NOW.minusDays(1),
                NOW
        )).isPresent());
    }

    @Test
    void commitCommandRejectsAnUnclaimedTask() {
        DataPullTask task = claimedTask();
        task.setState(TaskState.QUEUED);

        assertThrows(IllegalArgumentException.class, () -> new DataPullScopeProgressCommit(
                task,
                0L,
                false,
                null,
                NOW
        ));
    }

    @Test
    void progressIdentitiesMatchTheirPersistenceColumns() {
        DataPullScopeProgress progress = DataPullScopeProgress.initial(
                OperationCode.DP10, "s".repeat(96), NOW
        );
        progress.setLastAppliedBusinessWindowKey("w".repeat(160));
        progress.validate();

        progress.setScopeKey("s".repeat(97));
        assertThrows(IllegalArgumentException.class, progress::validate);
        progress.setScopeKey("s".repeat(96));
        progress.setLastAppliedBusinessWindowKey("w".repeat(161));
        assertThrows(IllegalArgumentException.class, progress::validate);
        assertThrows(IllegalArgumentException.class, () -> DataPullScopeProgress.initial(
                OperationCode.DP10, "s".repeat(97), NOW
        ));
    }

    private DataPullTask claimedTask() {
        DataPullTask task = DataPullTask.queued(
                10L,
                OperationCode.DP10,
                "ali1688-open-api",
                307L,
                null,
                "ali-account-307",
                null,
                null,
                null,
                null,
                "scope-10",
                NOW.minusHours(1),
                "DP10:2026-08-02",
                "FETCH_PAGE",
                NOW.minusHours(2)
        );
        task.setState(TaskState.RUNNING);
        task.setLeaseOwner("worker-1");
        task.setLeaseUntil(NOW.plusMinutes(5));
        task.setFenceEpoch(2L);
        task.setVersion(3L);
        return task;
    }
}
