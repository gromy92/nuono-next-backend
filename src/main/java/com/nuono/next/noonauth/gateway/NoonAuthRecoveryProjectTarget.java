package com.nuono.next.noonauth.gateway;

import java.util.Objects;

public final class NoonAuthRecoveryProjectTarget {
    private final Long ownerUserId;
    private final String projectCode;
    private final String storeCode;
    private final long expectedAuthVersion;

    public NoonAuthRecoveryProjectTarget(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            long expectedAuthVersion
    ) {
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        this.projectCode = Objects.requireNonNull(projectCode, "projectCode must not be null");
        this.storeCode = storeCode;
        this.expectedAuthVersion = Math.max(0L, expectedAuthVersion);
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public long getExpectedAuthVersion() {
        return expectedAuthVersion;
    }

    public String key() {
        return ownerUserId + ":" + projectCode;
    }
}
