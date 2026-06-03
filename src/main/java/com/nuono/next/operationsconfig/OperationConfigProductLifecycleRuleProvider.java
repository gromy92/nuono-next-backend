package com.nuono.next.operationsconfig;

import com.nuono.next.sales.ProductLifecycleCalculationScope;
import com.nuono.next.sales.ProductLifecycleRuleProvider;
import com.nuono.next.sales.ProductLifecycleRuleSet;
import org.springframework.stereotype.Component;

@Component
public class OperationConfigProductLifecycleRuleProvider implements ProductLifecycleRuleProvider {

    private final OperationConfigTypedVersionRepository repository;

    public OperationConfigProductLifecycleRuleProvider(OperationConfigTypedVersionRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProductLifecycleRuleSet resolve(ProductLifecycleCalculationScope scope) {
        return OperationConfigTypedVersionContentSupport.resolveEffectiveVersion(
                        repository,
                        OperationConfigVersionType.PRODUCT_LIFECYCLE,
                        scope == null ? null : scope.getOwnerUserId(),
                        scope == null ? null : scope.getStoreCode(),
                        scope == null ? null : scope.getSiteCode()
                )
                .map(this::toRuleSet)
                .orElseGet(ProductLifecycleRuleSet::defaultV1);
    }

    private ProductLifecycleRuleSet toRuleSet(OperationConfigTypedVersion version) {
        return new ProductLifecycleRuleSet(
                version.getVersionNo(),
                "SYSTEM_DEFAULT".equals(version.getStatus()),
                version.getVersionNo(),
                version.getDisplayName(),
                version.getSourceLabel(),
                OperationConfigTypedVersionContentSupport.lifecycleThresholds(version)
        );
    }
}
