package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.dp08.Dp08TaskFenceRow;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.Dp08RuntimeMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** Locks and validates the authoritative runtime task before competitor fact mutation. */
final class Dp08FactFence {
    private final Dp08RuntimeMapper mapper;

    Dp08FactFence(Dp08RuntimeMapper mapper) {
        this.mapper = mapper;
    }

    void require(DataPullTask task, OperationCode operation) {
        if (task == null || task.getId() == null || task.getFenceEpoch() == null
                || task.getLeaseOwner() == null) {
            throw new IllegalStateException("DP-08 fact write has no claimed runtime identity");
        }
        Dp08TaskFenceRow row = mapper.lockRuntimeTask(task.getId());
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        if (row == null
                || !Objects.equals(row.getId(), task.getId())
                || !operation.name().equals(row.getOperationCode())
                || !"RUNNING".equals(row.getState())
                || !Objects.equals(row.getFenceEpoch(), task.getFenceEpoch())
                || !Objects.equals(row.getLeaseOwner(), task.getLeaseOwner())
                || row.getLeaseUntil() == null
                || !row.getLeaseUntil().isAfter(nowUtc)) {
            throw new IllegalStateException("DP-08 runtime fact-write fence is stale");
        }
    }

    void requireStillLive(DataPullTask task) {
        if (task == null || task.getId() == null || task.getFenceEpoch() == null
                || task.getLeaseOwner() == null
                || mapper.countLiveRuntimeTask(
                        task.getId(),
                        task.getFenceEpoch(),
                        task.getLeaseOwner()
                ) != 1) {
            throw new IllegalStateException("DP-08 runtime fact-write lease expired before commit");
        }
    }
}
