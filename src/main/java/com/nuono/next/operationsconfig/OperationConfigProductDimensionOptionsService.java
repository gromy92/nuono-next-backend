package com.nuono.next.operationsconfig;

import com.nuono.next.permission.access.BusinessAccessContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.List;

@Service
public class OperationConfigProductDimensionOptionsService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final OperationConfigScopeService scopeService;
    private final OperationConfigProductDimensionRepository repository;

    public OperationConfigProductDimensionOptionsService(
            OperationConfigScopeService scopeService,
            OperationConfigProductDimensionRepository repository
    ) {
        this.scopeService = scopeService;
        this.repository = repository;
    }

    public OperationConfigProductDimensionOptionsView options(
            BusinessAccessContext context,
            List<Long> bossUserIds,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String brandQuery,
            String fulltypeQuery,
            Integer limit
    ) {
        scopeService.requireStoreSiteScope(context, bossUserIds, ownerUserId, storeCode, siteCode);
        int normalizedLimit = normalizedLimit(limit);
        return new OperationConfigProductDimensionOptionsView(
                true,
                "product_management",
                repository.listBrandOptions(ownerUserId, storeCode, normalize(brandQuery), normalizedLimit),
                repository.listProductFulltypeOptions(ownerUserId, storeCode, normalize(fulltypeQuery), normalizedLimit)
        );
    }

    private int normalizedLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
