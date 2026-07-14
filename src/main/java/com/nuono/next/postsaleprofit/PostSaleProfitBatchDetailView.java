package com.nuono.next.postsaleprofit;

import java.util.List;

public class PostSaleProfitBatchDetailView {
    private final Long batchId;
    private final List<PostSaleProfitOrderLineView> orderLines;

    public PostSaleProfitBatchDetailView(Long batchId, List<PostSaleProfitOrderLineView> orderLines) {
        this.batchId = batchId;
        this.orderLines = orderLines == null ? List.of() : List.copyOf(orderLines);
    }

    public Long getBatchId() { return batchId; }
    public List<PostSaleProfitOrderLineView> getOrderLines() { return orderLines; }
}
