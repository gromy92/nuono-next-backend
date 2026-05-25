package com.nuono.next.noonpull;

public class NoonPullTaskLookupQuery {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private NoonPullType pullType;
    private NoonPullDataDomain dataDomain;
    private String targetIdentityPrefix;

    public static Builder builder() {
        return new Builder();
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public NoonPullType getPullType() {
        return pullType;
    }

    public NoonPullDataDomain getDataDomain() {
        return dataDomain;
    }

    public String getTargetIdentityPrefix() {
        return targetIdentityPrefix;
    }

    public static final class Builder {
        private final NoonPullTaskLookupQuery query = new NoonPullTaskLookupQuery();

        public Builder ownerUserId(Long ownerUserId) {
            query.ownerUserId = ownerUserId;
            return this;
        }

        public Builder storeCode(String storeCode) {
            query.storeCode = storeCode;
            return this;
        }

        public Builder siteCode(String siteCode) {
            query.siteCode = siteCode;
            return this;
        }

        public Builder pullType(NoonPullType pullType) {
            query.pullType = pullType;
            return this;
        }

        public Builder dataDomain(NoonPullDataDomain dataDomain) {
            query.dataDomain = dataDomain;
            return this;
        }

        public Builder targetIdentityPrefix(String targetIdentityPrefix) {
            query.targetIdentityPrefix = targetIdentityPrefix;
            return this;
        }

        public NoonPullTaskLookupQuery build() {
            return query;
        }
    }
}
