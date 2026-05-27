package com.nuono.next.sales;

import com.nuono.next.operationsconfig.OperationLifecycleRuleThresholds;

public class ProductLifecycleRuleSet {

    private final String ruleVersion;
    private final boolean fallback;
    private final String lifecycleVersionNo;
    private final String lifecycleVersionName;
    private final String lifecycleVersionSourceLabel;
    private final OperationLifecycleRuleThresholds thresholds;

    public ProductLifecycleRuleSet(String ruleVersion, boolean fallback) {
        this(
                ruleVersion,
                fallback,
                "DEFAULT_LIFECYCLE_CONFIG",
                "默认生命周期配置",
                "系统默认",
                OperationLifecycleRuleThresholds.defaultV1()
        );
    }

    public ProductLifecycleRuleSet(
            String ruleVersion,
            boolean fallback,
            String lifecycleVersionNo,
            String lifecycleVersionName,
            String lifecycleVersionSourceLabel
    ) {
        this(ruleVersion, fallback, lifecycleVersionNo, lifecycleVersionName, lifecycleVersionSourceLabel,
                OperationLifecycleRuleThresholds.defaultV1());
    }

    public ProductLifecycleRuleSet(
            String ruleVersion,
            boolean fallback,
            String lifecycleVersionNo,
            String lifecycleVersionName,
            String lifecycleVersionSourceLabel,
            OperationLifecycleRuleThresholds thresholds
    ) {
        this.ruleVersion = ruleVersion == null || ruleVersion.isBlank()
                ? ProductLifecycleResult.DEFAULT_RULE_VERSION
                : ruleVersion;
        this.fallback = fallback;
        this.lifecycleVersionNo = lifecycleVersionNo == null || lifecycleVersionNo.isBlank()
                ? "DEFAULT_LIFECYCLE_CONFIG"
                : lifecycleVersionNo;
        this.lifecycleVersionName = lifecycleVersionName == null || lifecycleVersionName.isBlank()
                ? "默认生命周期配置"
                : lifecycleVersionName;
        this.lifecycleVersionSourceLabel = lifecycleVersionSourceLabel == null || lifecycleVersionSourceLabel.isBlank()
                ? "系统默认"
                : lifecycleVersionSourceLabel;
        this.thresholds = thresholds == null ? OperationLifecycleRuleThresholds.defaultV1() : thresholds;
    }

    public static ProductLifecycleRuleSet defaultV1() {
        return new ProductLifecycleRuleSet(ProductLifecycleResult.DEFAULT_RULE_VERSION, true);
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public boolean isFallback() {
        return fallback;
    }

    public String getLifecycleVersionNo() {
        return lifecycleVersionNo;
    }

    public String getLifecycleVersionName() {
        return lifecycleVersionName;
    }

    public String getLifecycleVersionSourceLabel() {
        return lifecycleVersionSourceLabel;
    }

    public OperationLifecycleRuleThresholds getThresholds() {
        return thresholds;
    }
}
