package com.nuono.next.operationsconfig;

import com.nuono.next.infrastructure.mapper.OperationConfigScopeMapper;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisOperationConfigScopeRepository implements OperationConfigScopeRepository {

    private final OperationConfigScopeMapper mapper;

    public MyBatisOperationConfigScopeRepository(OperationConfigScopeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<OperationConfigBossOption> listBossOptions() {
        return mapper.selectBossOptions();
    }

    @Override
    public List<OperationConfigStoreScope> listStoreSitesByBossUserIds(List<Long> bossUserIds) {
        if (bossUserIds == null || bossUserIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectStoreSitesByBossUserIds(bossUserIds);
    }

    @Override
    public List<OperationConfigStoreScope> listStoreSitesByStoreCodes(Set<String> storeCodes) {
        if (storeCodes == null || storeCodes.isEmpty()) {
            return List.of();
        }
        return mapper.selectStoreSitesByStoreCodes(storeCodes);
    }
}
