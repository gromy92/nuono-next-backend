package com.nuono.next.postsaleprofit;

public class PostSaleProfitBatchLockCommand {
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final Long batchId;
    private final boolean locked;
    private final String reason;

    public PostSaleProfitBatchLockCommand(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long batchId,
            boolean locked,
            String reason
    ) {
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.batchId = batchId;
        this.locked = locked;
        this.reason = reason;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public Long getBatchId() { return batchId; }
    public boolean isLocked() { return locked; }
    public String getReason() { return reason; }
}
