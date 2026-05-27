package com.nuono.next.operationsconfig;

import java.util.List;

public class OperationConfigBundleScopeCommand {

    private final List<Long> bossUserIds;
    private final List<OperationConfigBundleScopeStoreCommand> stores;

    public OperationConfigBundleScopeCommand(
            List<Long> bossUserIds,
            List<OperationConfigBundleScopeStoreCommand> stores
    ) {
        this.bossUserIds = bossUserIds == null ? List.of() : List.copyOf(bossUserIds);
        this.stores = stores == null ? List.of() : List.copyOf(stores);
    }

    public List<Long> getBossUserIds() {
        return bossUserIds;
    }

    public List<OperationConfigBundleScopeStoreCommand> getStores() {
        return stores;
    }
}
