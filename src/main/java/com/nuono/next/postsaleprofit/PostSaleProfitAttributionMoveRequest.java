package com.nuono.next.postsaleprofit;

import java.math.BigDecimal;

public class PostSaleProfitAttributionMoveRequest {
    private String storeCode;
    private String siteCode;
    private Long sourceBatchId;
    private Long targetBatchId;
    private BigDecimal quantity;
    private String reason;

    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public Long getSourceBatchId() { return sourceBatchId; }
    public void setSourceBatchId(Long sourceBatchId) { this.sourceBatchId = sourceBatchId; }
    public Long getTargetBatchId() { return targetBatchId; }
    public void setTargetBatchId(Long targetBatchId) { this.targetBatchId = targetBatchId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
