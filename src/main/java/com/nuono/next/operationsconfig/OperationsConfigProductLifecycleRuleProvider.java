package com.nuono.next.operationsconfig;

import com.nuono.next.sales.ProductLifecycleCalculationScope;
import com.nuono.next.sales.ProductLifecycleRuleProvider;
import com.nuono.next.sales.ProductLifecycleRuleSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OperationsConfigProductLifecycleRuleProvider implements ProductLifecycleRuleProvider {

    private final OperationLifecycleRuleRepository repository;
    private final OperationConfigBundleRepository bundleRepository;
    private final OperationConfigTypedVersionRepository typedVersionRepository;

    @Autowired
    public OperationsConfigProductLifecycleRuleProvider(
            OperationLifecycleRuleRepository repository,
            OperationConfigBundleRepository bundleRepository,
            OperationConfigTypedVersionRepository typedVersionRepository
    ) {
        this.repository = repository;
        this.bundleRepository = bundleRepository;
        this.typedVersionRepository = typedVersionRepository;
    }

    OperationsConfigProductLifecycleRuleProvider(
            OperationLifecycleRuleRepository repository,
            OperationConfigBundleRepository bundleRepository
    ) {
        this(repository, bundleRepository, null);
    }

    OperationsConfigProductLifecycleRuleProvider(OperationLifecycleRuleRepository repository) {
        this(repository, null, null);
    }

    @Override
    public ProductLifecycleRuleSet resolve(ProductLifecycleCalculationScope scope) {
        OperationConfigTypedVersionEvidence lifecycleVersion = OperationConfigTypedVersionEvidence.resolve(
                typedVersionRepository,
                OperationConfigVersionType.PRODUCT_LIFECYCLE,
                scope == null ? null : scope.getOwnerUserId(),
                scope == null ? null : scope.getStoreCode(),
                scope == null ? null : scope.getSiteCode()
        );
        OperationConfigTypedVersion typedVersion = OperationConfigTypedVersionContentSupport.resolveEffectiveVersion(
                typedVersionRepository,
                OperationConfigVersionType.PRODUCT_LIFECYCLE,
                scope == null ? null : scope.getOwnerUserId(),
                scope == null ? null : scope.getStoreCode(),
                scope == null ? null : scope.getSiteCode()
        ).orElse(null);
        if (typedVersion != null) {
            return ruleSet(
                    typedVersion.getVersionNo(),
                    "SYSTEM_DEFAULT".equals(typedVersion.getStatus()),
                    lifecycleVersion,
                    OperationConfigTypedVersionContentSupport.lifecycleThresholds(typedVersion)
            );
        }
        if (scope == null) {
            return ruleSet(ProductLifecycleRuleSet.defaultV1().getRuleVersion(), true, lifecycleVersion);
        }
        if (scope.isExplicitRuleVersion()) {
            return ruleSet(
                    scope.getRuleVersion(),
                    ProductLifecycleRuleSet.defaultV1().getRuleVersion().equals(scope.getRuleVersion()),
                    lifecycleVersion
            );
        }
        return repository.findLatestPublished(
                        scope.getOwnerUserId(),
                scope.getStoreCode(),
                scope.getSiteCode()
        )
                .map(rule -> ruleSet(ruleVersion(rule), false, lifecycleVersion))
                .orElse(ruleSet(ProductLifecycleRuleSet.defaultV1().getRuleVersion(), true, lifecycleVersion));
    }

    private ProductLifecycleRuleSet ruleSet(
            String ruleVersion,
            boolean fallback,
            OperationConfigTypedVersionEvidence lifecycleVersion
    ) {
        return ruleSet(ruleVersion, fallback, lifecycleVersion, OperationLifecycleRuleThresholds.defaultV1());
    }

    private ProductLifecycleRuleSet ruleSet(
            String ruleVersion,
            boolean fallback,
            OperationConfigTypedVersionEvidence lifecycleVersion,
            OperationLifecycleRuleThresholds thresholds
    ) {
        return new ProductLifecycleRuleSet(
                ruleVersion,
                fallback,
                lifecycleVersion.getVersionNo(),
                lifecycleVersion.getVersionName(),
                lifecycleVersion.getSourceLabel(),
                thresholds
        );
    }

    private String ruleVersion(OperationLifecycleRule rule) {
        if (rule.getBundleVersionId() == null) {
            return rule.getRuleVersion();
        }
        if (bundleRepository == null) {
            return "OPS_CONFIG_" + rule.getBundleVersionId();
        }
        return bundleRepository.findBundle(rule.getBundleVersionId())
                .map(OperationConfigBundle::getVersionNo)
                .orElse("OPS_CONFIG_" + rule.getBundleVersionId());
    }
}
