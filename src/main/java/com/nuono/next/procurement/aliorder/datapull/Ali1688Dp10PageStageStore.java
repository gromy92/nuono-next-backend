package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.LocalDateTime;
import java.util.Optional;

/** Durable DP-10 generation/pass staging Seam; cleanup is isolated behind its bounded Interface. */
public interface Ali1688Dp10PageStageStore {
    Optional<Ali1688Dp10StagedPage> load(
            DataPullTask task,
            long generationNo,
            int scanPass,
            Ali1688HistoricalOrderProvider.Partition partition,
            int pageNo,
            LocalDateTime nowUtc
    );

    Ali1688Dp10StagedPage stageList(
            DataPullTask task,
            long generationNo,
            int scanPass,
            Ali1688Dp10ValidatedPage page,
            LocalDateTime nowUtc
    );

    Ali1688Dp10SealBatch readSealBatch(
            DataPullTask task,
            long generationNo,
            Ali1688HistoricalOrderProvider.Partition partition,
            String afterFingerprint,
            LocalDateTime nowUtc
    );

    Optional<Ali1688Dp10PendingItem> nextPendingDetail(
            DataPullTask task,
            long generationNo,
            LocalDateTime nowUtc
    );

    Ali1688Dp10StagedPage recordDetail(
            DataPullTask task,
            Ali1688Dp10PendingItem item,
            Ali1688Dp10DetailDecision decision,
            LocalDateTime nowUtc
    );
}
