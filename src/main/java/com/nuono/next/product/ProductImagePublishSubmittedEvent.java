package com.nuono.next.product;

final class ProductImagePublishSubmittedEvent {
    private final Long suiteId;
    private final Long ownerUserId;
    private final String storeCode;
    private final Long operatorUserId;
    private final String attemptId;

    ProductImagePublishSubmittedEvent(
            Long suiteId,
            Long ownerUserId,
            String storeCode,
            Long operatorUserId,
            String attemptId
    ) {
        this.suiteId = suiteId;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.operatorUserId = operatorUserId;
        this.attemptId = attemptId;
    }

    Long suiteId() { return suiteId; }
    Long ownerUserId() { return ownerUserId; }
    String storeCode() { return storeCode; }
    Long operatorUserId() { return operatorUserId; }
    String attemptId() { return attemptId; }
}
