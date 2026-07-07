package com.nuono.next.postsaleprofit;

import java.util.List;

public class PostSaleProfitAttributionAdjustmentView {
    private final Long batchId;
    private final List<String> qualityStatuses;

    public PostSaleProfitAttributionAdjustmentView(Long batchId, List<String> qualityStatuses) {
        this.batchId = batchId;
        this.qualityStatuses = qualityStatuses == null ? List.of() : List.copyOf(qualityStatuses);
    }

    public Long getBatchId() { return batchId; }
    public List<String> getQualityStatuses() { return qualityStatuses; }
}
