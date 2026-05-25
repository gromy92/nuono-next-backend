package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.List;

public class NoonProductRefreshCommand {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String skuParent;
    private NoonProductRefreshReason reason;
    private boolean hasLocalDraft;
    private NoonProductRefreshMergePolicy mergePolicy;
    private List<String> allSkuParents = new ArrayList<>();

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

    public String getSkuParent() {
        return skuParent;
    }

    public NoonProductRefreshReason getReason() {
        return reason;
    }

    public boolean isHasLocalDraft() {
        return hasLocalDraft;
    }

    public NoonProductRefreshMergePolicy getMergePolicy() {
        return mergePolicy;
    }

    public List<String> getAllSkuParents() {
        return allSkuParents;
    }

    public static class Builder {
        private final NoonProductRefreshCommand command = new NoonProductRefreshCommand();

        public Builder ownerUserId(Long ownerUserId) {
            command.ownerUserId = ownerUserId;
            return this;
        }

        public Builder storeCode(String storeCode) {
            command.storeCode = storeCode;
            return this;
        }

        public Builder siteCode(String siteCode) {
            command.siteCode = siteCode;
            return this;
        }

        public Builder skuParent(String skuParent) {
            command.skuParent = skuParent;
            return this;
        }

        public Builder reason(NoonProductRefreshReason reason) {
            command.reason = reason;
            return this;
        }

        public Builder hasLocalDraft(boolean hasLocalDraft) {
            command.hasLocalDraft = hasLocalDraft;
            return this;
        }

        public Builder mergePolicy(NoonProductRefreshMergePolicy mergePolicy) {
            command.mergePolicy = mergePolicy;
            return this;
        }

        public Builder allSkuParents(List<String> allSkuParents) {
            command.allSkuParents = allSkuParents == null ? new ArrayList<>() : allSkuParents;
            return this;
        }

        public NoonProductRefreshCommand build() {
            return command;
        }
    }
}
