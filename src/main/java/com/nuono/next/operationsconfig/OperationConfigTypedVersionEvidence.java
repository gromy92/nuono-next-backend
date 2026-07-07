package com.nuono.next.operationsconfig;

public class OperationConfigTypedVersionEvidence {
    public static final String DEFAULT_CALENDAR_VERSION_NO = "DEFAULT_CALENDAR_CONFIG";
    public static final String DEFAULT_LIFECYCLE_VERSION_NO = "DEFAULT_LIFECYCLE_CONFIG";

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
        return OperationConfigTypedVersionContentSupport.resolveEffectiveVersion(
                        repository,
                        configType,
                        ownerUserId,
                        storeCode,
                        siteCode
                )
                .map(version -> new OperationConfigTypedVersionEvidence(
                        configType.name(),
                        version.getVersionNo(),
                        version.getDisplayName(),
                        hasText(version.getSourceLabel()) ? version.getSourceLabel() : "typed_version"
                ))
                .orElseGet(() -> defaultFor(configType));
    }

    public static OperationConfigTypedVersionEvidence defaultFor(OperationConfigVersionType configType) {
        if (OperationConfigVersionType.PRODUCT_LIFECYCLE.equals(configType)) {
            return new OperationConfigTypedVersionEvidence(
                    configType.name(),
                    DEFAULT_LIFECYCLE_VERSION_NO,
                    "默认生命周期配置",
                    "系统默认"
            );
        }
        return new OperationConfigTypedVersionEvidence(
                configType.name(),
                DEFAULT_CALENDAR_VERSION_NO,
                "默认日历配置",
                "系统默认"
        );
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
