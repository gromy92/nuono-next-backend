package com.nuono.next.postsaleprofit;

import java.math.BigDecimal;

public class PostSaleProfitAttributionMoveView {
    private final Long sourceBatchId;
    private final Long targetBatchId;
    private final BigDecimal movedQuantity;

    public PostSaleProfitAttributionMoveView(Long sourceBatchId, Long targetBatchId, BigDecimal movedQuantity) {
        this.sourceBatchId = sourceBatchId;
        this.targetBatchId = targetBatchId;
        this.movedQuantity = movedQuantity;
    }

    public Long getSourceBatchId() { return sourceBatchId; }
    public Long getTargetBatchId() { return targetBatchId; }
    public BigDecimal getMovedQuantity() { return movedQuantity; }
}
