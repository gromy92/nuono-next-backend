package com.nuono.next.productpublicdetail;

public enum ProductPublicDetailSyncStatus {
    SUCCEEDED,
    PARTIAL,
    NOT_FOUND,
    FAILED;

    public boolean updatesLatestPointer() {
        return this == SUCCEEDED || this == PARTIAL;
    }
}
