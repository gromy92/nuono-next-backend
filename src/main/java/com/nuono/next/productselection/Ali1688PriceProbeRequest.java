package com.nuono.next.productselection;

public class Ali1688PriceProbeRequest {

    public final Ali1688CollectionRecords.TaskRecord task;
    public final Ali1688CollectionRecords.CandidateRecord candidate;
    public final Integer quantity;
    public final String skuText;
    public final String regionText;
    public final String safetyMode;
    public final Long operatorUserId;

    public Ali1688PriceProbeRequest(
            Ali1688CollectionRecords.TaskRecord task,
            Ali1688CollectionRecords.CandidateRecord candidate,
            Integer quantity,
            String skuText,
            String regionText,
            String safetyMode,
            Long operatorUserId
    ) {
        this.task = task;
        this.candidate = candidate;
        this.quantity = quantity;
        this.skuText = skuText;
        this.regionText = regionText;
        this.safetyMode = safetyMode;
        this.operatorUserId = operatorUserId;
    }
}
