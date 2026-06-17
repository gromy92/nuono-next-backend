package com.nuono.next.intransit;

import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import org.springframework.util.StringUtils;

public class InTransitManualEditGuard {

    private static final String LOCKED_MESSAGE = "已在途批次仅允许维护商品明细。";

    public void requireBatchBaseEditable(BatchView batch) {
        requireDraftBatch(batch);
    }

    public void requireLogisticsNodesEditable(BatchView batch) {
        requireDraftBatch(batch);
    }

    private void requireDraftBatch(BatchView batch) {
        if (batch == null) {
            throw new IllegalArgumentException("在途批次不存在。");
        }
        String status = batch.getBatchStatus();
        if (StringUtils.hasText(status) && !InTransitBatchStatus.DRAFT.code().equals(status)) {
            throw new IllegalStateException(LOCKED_MESSAGE);
        }
    }
}
