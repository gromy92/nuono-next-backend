package com.nuono.next.operationsconfig;

import java.util.List;
import java.util.Set;

public interface OperationConfigScopeRepository {

    List<OperationConfigBossOption> listBossOptions();

    List<OperationConfigStoreScope> listStoreSitesByBossUserIds(List<Long> bossUserIds);

    List<OperationConfigStoreScope> listStoreSitesByStoreCodes(Set<String> storeCodes);
}
