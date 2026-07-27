package com.nuono.next.productpublicdetail;

public final class ProductPublicDetailFetchResult {
    private final ProductPublicDetailSyncStatus status;
    private final String message;

    private ProductPublicDetailFetchResult(ProductPublicDetailSyncStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public static ProductPublicDetailFetchResult of(ProductPublicDetailSyncStatus status, String message) {
        return new ProductPublicDetailFetchResult(status, message);
    }

    public ProductPublicDetailSyncStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public boolean isUsable() {
        return status == ProductPublicDetailSyncStatus.SUCCEEDED
                || status == ProductPublicDetailSyncStatus.PARTIAL;
    }

    public boolean isExplicitlyNotFound() {
        return status == ProductPublicDetailSyncStatus.NOT_FOUND;
    }
}
