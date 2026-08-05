package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.persistence.DataPullTask;
import java.time.LocalDateTime;

/** Fenced bounded cleanup Seam for one task; every call mutates at most one stage table. */
public interface Ali1688Dp10StageCleanup {

    Ali1688Dp10StageCleanupAdvance cleanupOlderGenerations(
            DataPullTask task,
            long currentGenerationNo,
            LocalDateTime nowUtc
    );

    Ali1688Dp10StageCleanupAdvance cleanupCurrentGeneration(
            DataPullTask task,
            long currentGenerationNo,
            LocalDateTime nowUtc
    );
}
