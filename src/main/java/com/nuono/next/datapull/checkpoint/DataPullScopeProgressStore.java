package com.nuono.next.datapull.checkpoint;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.LocalDateTime;
import java.util.Optional;

/** Persistence seam for full-to-incremental progress; callers never write a high-water mark directly. */
public interface DataPullScopeProgressStore {

    DataPullScopeProgress getOrCreate(
            OperationCode operationCode,
            String scopeKey,
            LocalDateTime nowUtc
    );

    Optional<DataPullScopeProgress> commitCompletedWindow(DataPullScopeProgressCommit commit);
}
