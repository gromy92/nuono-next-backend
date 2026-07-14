package com.nuono.next.postsaleprofit;

import java.math.BigDecimal;

public class PostSaleProfitAttributionMoveCommand {
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final Long sourceBatchId;
    private final Long targetBatchId;
    private final BigDecimal quantity;
    private final String reason;

    public PostSaleProfitAttributionMoveCommand(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long sourceBatchId,
            Long targetBatchId,
            BigDecimal quantity,
            String reason
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.sourceBatchId = sourceBatchId;
        this.targetBatchId = targetBatchId;
        this.quantity = quantity;
        this.reason = reason;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public Long getSourceBatchId() { return sourceBatchId; }
    public Long getTargetBatchId() { return targetBatchId; }
    public BigDecimal getQuantity() { return quantity; }
    public String getReason() { return reason; }
}
