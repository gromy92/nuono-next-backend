package com.nuono.next.noonsync;

public class NoonSyncPlanDefinition {

    private final String key;
    private final NoonSyncDataDomain dataDomain;
    private final NoonSyncTriggerMode triggerMode;
    private final String description;

    public NoonSyncPlanDefinition(
            String key,
            NoonSyncDataDomain dataDomain,
            NoonSyncTriggerMode triggerMode,
            String description
    ) {
        this.key = key;
        this.dataDomain = dataDomain;
        this.triggerMode = triggerMode;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public NoonSyncDataDomain getDataDomain() {
        return dataDomain;
    }

    public NoonSyncTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public String getDescription() {
        return description;
    }
}
