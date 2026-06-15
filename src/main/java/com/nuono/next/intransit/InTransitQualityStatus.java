package com.nuono.next.intransit;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum InTransitQualityStatus {
    FORWARDER_UNMATCHED("forwarder_unmatched", "货代未归一"),
    FORWARDER_MATCHED("forwarder_matched", "货代已归一");

    private final String code;
    private final String label;

    InTransitQualityStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(InTransitQualityStatus::code).collect(Collectors.toList());
    }
}
