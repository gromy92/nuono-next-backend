package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.InMemoryDataPullTaskStore;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;

/** Shared deterministic runtime fixture; contains no assertions or scenario policy. */
abstract class RuntimeExecutorTestFixture {
    protected static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 3, 0);

    protected RuntimeExecutor executor(
            InMemoryDataPullTaskStore store,
            Function<ExecutionContext, AdvanceResult> advance
    ) {
        return executor(store, new InMemoryBackoffHoldStore(), advance, NOW.plusSeconds(10));
    }

    protected RuntimeExecutor executor(
            InMemoryDataPullTaskStore store,
            InMemoryBackoffHoldStore holds,
            Function<ExecutionContext, AdvanceResult> advance
    ) {
        return executor(store, holds, advance, NOW.plusSeconds(10));
    }

    protected RuntimeExecutor executor(
            InMemoryDataPullTaskStore store,
            Function<ExecutionContext, AdvanceResult> advance,
            LocalDateTime completedAtUtc
    ) {
        return executor(store, new InMemoryBackoffHoldStore(), advance, completedAtUtc);
    }

    protected RuntimeExecutor executor(
            InMemoryDataPullTaskStore store,
            InMemoryBackoffHoldStore holds,
            Function<ExecutionContext, AdvanceResult> advance,
            LocalDateTime completedAtUtc
    ) {
        DataPullJob job = new TestDataPullJob(
                OperationCode.DP04,
                "noon-partner",
                List.of(),
                advance
        );
        Instant completedAt = completedAtUtc.toInstant(ZoneOffset.UTC);
        return new RuntimeExecutor(
                new DataPullJobRegistry(List.of(job)),
                store,
                holds,
                Clock.fixed(completedAt, ZoneOffset.UTC)
        );
    }

    protected DataPullTask claimed(InMemoryDataPullTaskStore store, String owner) {
        return claimed(store, owner, NOW.plusMinutes(10));
    }

    protected DataPullTask claimed(
            InMemoryDataPullTaskStore store,
            String owner,
            LocalDateTime leaseUntil
    ) {
        DataPullTask queued = store.enqueue(DataPullTask.queued(
                store.nextTaskId(),
                OperationCode.DP04,
                "noon-partner",
                307L,
                108065L,
                "account-307",
                "egress-cn-1",
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "scope-sa",
                NOW,
                "2026-08-02",
                "FETCH",
                NOW.minusMinutes(1)
        ));
        return store.claim(
                queued.getId(),
                queued.getVersion(),
                owner,
                leaseUntil,
                NOW
        ).orElseThrow();
    }
}
