package com.nuono.next.operationsconfig;

public class OperationLifecycleRuleView {

    private final Long id;
    private final Long ownerUserId;
    private final String storeCode;
    private final String siteCode;
    private final String ruleVersion;
    private final String sourceRuleVersion;
    private final OperationLifecycleRuleThresholds thresholds;
    private final Long bundleVersionId;
    private final OperationConfigPublishStatus publishStatus;
    private final String publishSourceRole;
    private final String publishSourceLabel;
    private final boolean fallback;

    public OperationLifecycleRuleView(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleVersion,
            String sourceRuleVersion,
            OperationLifecycleRuleThresholds thresholds,
            OperationConfigPublishStatus publishStatus,
            boolean fallback
    ) {
        this(
                id,
                ownerUserId,
                storeCode,
                siteCode,
                ruleVersion,
                sourceRuleVersion,
                thresholds,
                null,
                publishStatus,
                null,
                null,
                fallback
        );
    }

    public OperationLifecycleRuleView(
            Long id,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            String ruleVersion,
            String sourceRuleVersion,
            OperationLifecycleRuleThresholds thresholds,
            Long bundleVersionId,
            OperationConfigPublishStatus publishStatus,
            String publishSourceRole,
            String publishSourceLabel,
            boolean fallback
    ) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.storeCode = storeCode;
        this.siteCode = siteCode;
        this.ruleVersion = ruleVersion;
        this.sourceRuleVersion = sourceRuleVersion;
        this.thresholds = thresholds;
        this.bundleVersionId = bundleVersionId;
        this.publishStatus = publishStatus;
        this.publishSourceRole = OperationConfigVersionSource.safeRole(publishSourceRole);
        this.publishSourceLabel = fallback
                ? "系统默认"
                : OperationConfigVersionSource.safeLabel(publishSourceRole, publishSourceLabel);
        this.fallback = fallback;
    }

    public static OperationLifecycleRuleView fallback(
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        return new OperationLifecycleRuleView(
                null,
                ownerUserId,
                storeCode,
                siteCode,
                "DEFAULT_V1",
                null,
                OperationLifecycleRuleThresholds.defaultV1(),
                null,
                OperationConfigPublishStatus.PUBLISHED,
                OperationConfigVersionSource.SYSTEM_ADMIN,
                "系统默认",
                true
        );
    }

    public static OperationLifecycleRuleView fromRule(OperationLifecycleRule rule) {
        return new OperationLifecycleRuleView(
                rule.getId(),
                rule.getOwnerUserId(),
                rule.getStoreCode(),
                rule.getSiteCode(),
                rule.getRuleVersion(),
                rule.getSourceRuleVersion(),
                rule.getThresholds(),
                rule.getBundleVersionId(),
                rule.getPublishStatus(),
                rule.getPublishSourceRole(),
                rule.getPublishSourceLabel(),
                false
        );
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStoreCode() { return storeCode; }
    public String getSiteCode() { return siteCode; }
    public String getRuleVersion() { return ruleVersion; }
    public String getSourceRuleVersion() { return sourceRuleVersion; }
    public OperationLifecycleRuleThresholds getThresholds() { return thresholds; }
    public Long getBundleVersionId() { return bundleVersionId; }
    public OperationConfigPublishStatus getPublishStatus() { return publishStatus; }
    public String getPublishSourceRole() { return publishSourceRole; }
    public String getPublishSourceLabel() { return publishSourceLabel; }
    public boolean isFallback() { return fallback; }
}
