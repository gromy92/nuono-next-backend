package com.nuono.next.officialwarehouse;

public class OfficialWarehouseShippingBatchDiagnosticRecord {
    public Long id;
    public String batchNo;
    public String targetStoreCode;
    public String targetSiteCode;
    public String status;
    public String latestNodeStatus;
    public Integer packageCount;
    public Integer sourceCandidateCount;
    public Integer currentScopeCandidateCount;
    public Integer unmatchedCandidateCount;
    public Integer excludedCandidateCount;
    public Integer goodsLineCount;
    public Integer resolvedLineCount;
    public Integer shippedQuantity;
    public Integer remainingQuantity;
}
