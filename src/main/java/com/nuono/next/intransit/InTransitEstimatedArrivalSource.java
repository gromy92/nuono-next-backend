package com.nuono.next.intransit;

public enum InTransitEstimatedArrivalSource {
    MANUAL("MANUAL"),
    OFFICIAL("OFFICIAL"),
    AIR_HISTORY_ESTIMATE("AIR_HISTORY_ESTIMATE"),
    PLUGIN_REPORTED("PLUGIN_REPORTED"),
    LEGACY_IMPORTED("LEGACY_IMPORTED");

    private final String code;

    InTransitEstimatedArrivalSource(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
