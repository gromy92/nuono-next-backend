package com.nuono.next.operationsconfig;

import java.util.List;

public class OperationConfigScopeView {

    private final boolean systemAdmin;
    private final String roleName;
    private final List<OperationConfigBossOption> bossOptions;
    private final List<Long> selectedBossUserIds;
    private final List<OperationConfigStoreScope> stores;
    private final Long defaultOwnerUserId;
    private final String defaultStoreCode;
    private final String defaultSiteCode;
    private final String emptyReason;

    public OperationConfigScopeView(
            boolean systemAdmin,
            String roleName,
            List<OperationConfigBossOption> bossOptions,
            List<Long> selectedBossUserIds,
            List<OperationConfigStoreScope> stores,
            Long defaultOwnerUserId,
            String defaultStoreCode,
            String defaultSiteCode,
            String emptyReason
    ) {
        this.systemAdmin = systemAdmin;
        this.roleName = roleName;
        this.bossOptions = bossOptions;
        this.selectedBossUserIds = selectedBossUserIds;
        this.stores = stores;
        this.defaultOwnerUserId = defaultOwnerUserId;
        this.defaultStoreCode = defaultStoreCode;
        this.defaultSiteCode = defaultSiteCode;
        this.emptyReason = emptyReason;
    }

    public boolean isSystemAdmin() {
        return systemAdmin;
    }

    public String getRoleName() {
        return roleName;
    }

    public List<OperationConfigBossOption> getBossOptions() {
        return bossOptions;
    }

    public List<Long> getSelectedBossUserIds() {
        return selectedBossUserIds;
    }

    public List<OperationConfigStoreScope> getStores() {
        return stores;
    }

    public Long getDefaultOwnerUserId() {
        return defaultOwnerUserId;
    }

    public String getDefaultStoreCode() {
        return defaultStoreCode;
    }

    public String getDefaultSiteCode() {
        return defaultSiteCode;
    }

    public String getEmptyReason() {
        return emptyReason;
    }
}
