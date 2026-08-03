package com.nuono.next.noonauth.gateway;

import com.nuono.next.noonauth.NoonAuthRecoveryTargetPolicy;
import java.util.Objects;

public final class NoonAuthRecoveryProjectTarget {
    private final Long ownerUserId;
    private final String projectCode;
    private final String storeCode;
    private final String siteCode;
    private final long expectedAuthVersion;

    public NoonAuthRecoveryProjectTarget(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            long expectedAuthVersion
    ) {
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        this.projectCode = NoonAuthRecoveryTargetPolicy.normalize(
                Objects.requireNonNull(projectCode, "projectCode must not be null")
        );
        this.storeCode = NoonAuthRecoveryTargetPolicy.normalize(storeCode);
        this.siteCode = NoonAuthRecoveryTargetPolicy.normalizeSite(siteCode);
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

    public String getSiteCode() {
        return siteCode;
    }

    public boolean hasCompleteBusinessIdentity() {
        return NoonAuthRecoveryTargetPolicy.hasCompleteBusinessIdentity(
                ownerUserId,
                projectCode,
                storeCode,
                siteCode
        );
    }

    public long getExpectedAuthVersion() {
        return expectedAuthVersion;
    }

    public String key() {
        return ownerUserId + ":" + projectCode;
    }
}
