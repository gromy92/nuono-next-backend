package com.nuono.next.operationsconfig;

import java.util.List;
import java.util.stream.Collectors;

public class OperationConfigBundleScopeRequest {

    private List<Long> bossUserIds;
    private List<ScopeStore> stores;

    public List<Long> getBossUserIds() {
        return bossUserIds;
    }

    public void setBossUserIds(List<Long> bossUserIds) {
        this.bossUserIds = bossUserIds;
    }

    public List<ScopeStore> getStores() {
        return stores;
    }

    public void setStores(List<ScopeStore> stores) {
        this.stores = stores;
    }

    public OperationConfigBundleScopeCommand toCommand() {
        return new OperationConfigBundleScopeCommand(
                bossUserIds,
                stores == null
                        ? List.of()
                        : stores.stream()
                                .map(store -> new OperationConfigBundleScopeStoreCommand(
                                        store.getOwnerUserId(),
                                        store.getStoreCode(),
                                        store.getSiteCode()
                                ))
                                .collect(Collectors.toList())
        );
    }

    public static class ScopeStore {
        private Long ownerUserId;
        private String storeCode;
        private String siteCode;

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public void setSiteCode(String siteCode) {
            this.siteCode = siteCode;
        }
    }
}
