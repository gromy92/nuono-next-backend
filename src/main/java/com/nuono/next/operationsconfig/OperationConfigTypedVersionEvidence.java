package com.nuono.next.operationsconfig;

public class OperationConfigTypedVersionEvidence {
    public static final String DEFAULT_CALENDAR_VERSION_NO = OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO;
    public static final String DEFAULT_LIFECYCLE_VERSION_NO = OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO;
    public static final String DEFAULT_REPLENISHMENT_PLAN_VERSION_NO =
            OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO;

    private final String configType;
    private final String versionNo;
    private final String versionName;
    private final String sourceLabel;

    public OperationConfigTypedVersionEvidence(
            String configType,
            String versionNo,
            String versionName,
            String sourceLabel
    ) {
        this.configType = configType;
        this.versionNo = versionNo;
        this.versionName = versionName;
        this.sourceLabel = sourceLabel;
    }

    public static OperationConfigTypedVersionEvidence resolve(
            OperationConfigTypedVersionRepository repository,
            OperationConfigVersionType configType,
            Long ownerUserId,
            String storeCode,
            String siteCode
    ) {
        OperationConfigVersionType supportedType = requireSupported(configType);
        return OperationConfigTypedVersionContentSupport.resolveEffectiveVersion(
                        repository,
                        supportedType,
                        ownerUserId,
                        storeCode,
                        siteCode
                )
                .map(version -> fromVersion(supportedType, version))
                .orElseGet(() -> defaultFor(supportedType));
    }

    public static OperationConfigTypedVersionEvidence defaultFor(OperationConfigVersionType configType) {
        OperationConfigVersionType supportedType = requireSupported(configType);
        if (OperationConfigVersionType.BUSINESS_CALENDAR.equals(supportedType)) {
            return new OperationConfigTypedVersionEvidence(
                    supportedType.name(),
                    DEFAULT_CALENDAR_VERSION_NO,
                    "默认日历配置",
                    "系统默认"
            );
        }
        if (OperationConfigVersionType.PRODUCT_LIFECYCLE.equals(supportedType)) {
            return new OperationConfigTypedVersionEvidence(
                    supportedType.name(),
                    DEFAULT_LIFECYCLE_VERSION_NO,
                    "默认生命周期配置",
                    "系统默认"
            );
        }
        return new OperationConfigTypedVersionEvidence(
                supportedType.name(),
                DEFAULT_REPLENISHMENT_PLAN_VERSION_NO,
                "默认补货计划参数",
                "系统默认"
        );
    }

    private static OperationConfigTypedVersionEvidence fromVersion(
            OperationConfigVersionType configType,
            OperationConfigTypedVersion version
    ) {
        OperationConfigTypedVersionEvidence fallback = defaultFor(configType);
        return new OperationConfigTypedVersionEvidence(
                configType.name(),
                textOrFallback(version.getVersionNo(), fallback.getVersionNo()),
                textOrFallback(version.getDisplayName(), fallback.getVersionName()),
                textOrFallback(version.getSourceLabel(), fallback.getSourceLabel())
        );
    }

    private static OperationConfigVersionType requireSupported(OperationConfigVersionType configType) {
        if (configType == null) {
            throw new IllegalArgumentException("operation config version type is required");
        }
        if (OperationConfigVersionType.BUSINESS_CALENDAR.equals(configType)
                || OperationConfigVersionType.PRODUCT_LIFECYCLE.equals(configType)
                || OperationConfigVersionType.REPLENISHMENT_PLAN.equals(configType)) {
            return configType;
        }
        throw new IllegalArgumentException("unsupported operation config version type");
    }

    private static String textOrFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public String getConfigType() {
        return configType;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
