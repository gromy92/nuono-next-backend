package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskRepairCommand;
import com.nuono.next.datapull.persistence.DataPullTaskTransition;
import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DataPullTaskRepairServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 3, 0);
    private static final LocalDateTime REPAIR_AT = NOW.plusMinutes(2);

    @Test
    void requeuesTheSameWindowHandleAndCheckpointThroughAnExactVersionFence() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask failed = fail(store, OperationCode.DP02, "REPORT_CHECKPOINT_INVALID");
        DataPullTaskRepairCommand command = command(failed);

        DataPullTask repaired = service(store).repair(command).orElseThrow();

        assertEquals(failed.getId(), repaired.getId());
        assertEquals(failed.getBusinessWindowKey(), repaired.getBusinessWindowKey());
        assertEquals(failed.getStepCode(), repaired.getStepCode());
        assertEquals(failed.getRemoteHandle(), repaired.getRemoteHandle());
        assertEquals(failed.getCheckpoint(), repaired.getCheckpoint());
        assertEquals(TaskState.QUEUED, repaired.getState());
        assertEquals(failed.getVersion() + 1L, repaired.getVersion());
        assertEquals(failed.getFenceEpoch(), repaired.getFenceEpoch());
        assertNull(repaired.getSanitizedFailureCode());
        assertNull(repaired.getFinishedAt());

        assertFalse(store.transition(new DataPullTaskTransition(
                failed.getId(), failed.getFenceEpoch(), failed.getVersion(), "old-worker",
                TaskState.SUCCEEDED, failed.getStepCode(), failed.getRemoteHandle(),
                failed.getCheckpoint(), null, null, REPAIR_AT, REPAIR_AT
        )));
        DataPullTask reclaimed = store.claim(
                repaired.getId(), repaired.getVersion(), "repair-worker",
                REPAIR_AT.plusMinutes(5), REPAIR_AT
        ).orElseThrow();
        assertEquals(failed.getFenceEpoch() + 1L, reclaimed.getFenceEpoch());
    }

    @Test
    void refusesRepairWhileANewerWindowIsStillLive() {
        InMemoryDataPullTaskStore store = new InMemoryDataPullTaskStore();
        DataPullTask failed = fail(store, OperationCode.DP02, "REPORT_CHECKPOINT_INVALID");
        store.enqueue(task(store, OperationCode.DP02, NOW.plusDays(1), "2026-08-02"));

        assertTrue(service(store).repair(command(failed)).isEmpty());
        assertEquals(TaskState.FAILED, store.find(failed.getId()).orElseThrow().getState());
    }

    @Test
    void rejectsUnknownExternalWriteDp10AndReplaceableCurrentOperations() {
        InMemoryDataPullTaskStore unknownStore = new InMemoryDataPullTaskStore();
        DataPullTask unknown = fail(
                unknownStore, OperationCode.DP02, "REPORT_CREATE_OUTCOME_UNKNOWN"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service(unknownStore).repair(command(unknown))
        );

        InMemoryDataPullTaskStore dp10Store = new InMemoryDataPullTaskStore();
        DataPullTask dp10 = fail(dp10Store, OperationCode.DP10, "DP10_PROVIDER_TERMINAL");
        assertThrows(
                IllegalArgumentException.class,
                () -> service(dp10Store).repair(command(dp10))
        );

        InMemoryDataPullTaskStore currentStore = new InMemoryDataPullTaskStore();
        DataPullTask current = fail(currentStore, OperationCode.DP04, "SNAPSHOT_CONTRACT_INVALID");
        assertThrows(
                IllegalArgumentException.class,
                () -> service(currentStore).repair(command(current))
        );
    }

    private DataPullTask fail(
            InMemoryDataPullTaskStore store,
            OperationCode operation,
            String failureCode
    ) {
        DataPullTask queued = store.enqueue(task(store, operation, NOW, "2026-08-01"));
        DataPullTask claimed = store.claim(
                queued.getId(), queued.getVersion(), "old-worker",
                NOW.plusMinutes(10), NOW
        ).orElseThrow();
        assertTrue(store.transition(new DataPullTaskTransition(
                claimed.getId(), claimed.getFenceEpoch(), claimed.getVersion(), "old-worker",
                TaskState.FAILED, "POLL", "remote-handle", "checkpoint-v2",
                null, failureCode, NOW.plusMinutes(1), NOW.plusMinutes(1)
        )));
        return store.find(claimed.getId()).orElseThrow();
    }

    private DataPullTask task(
            InMemoryDataPullTaskStore store,
            OperationCode operation,
            LocalDateTime slot,
            String day
    ) {
        return DataPullTask.queued(
                store.nextTaskId(), operation, provider(operation), 307L, 108065L,
                "account-307", "egress-1", "PRJ108065", "STR108065-NSA", "SA",
                "scope-sa", slot, operation.name() + ":date-range:" + day + ".." + day,
                "CREATE", NOW.minusMinutes(1)
        );
    }

    private String provider(OperationCode operation) {
        return operation == OperationCode.DP10 ? "ALI1688_OPEN_API" : "NOON_REPORT";
    }

    private DataPullTaskRepairCommand command(DataPullTask failed) {
        return new DataPullTaskRepairCommand(
                failed.getId(), failed.getVersion(), failed.getFenceEpoch(),
                failed.getSanitizedFailureCode()
        );
    }

    private DataPullTaskRepairService service(InMemoryDataPullTaskStore store) {
        return new DataPullTaskRepairService(
                store,
                Clock.fixed(REPAIR_AT.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        );
    }
}
