package com.nuono.next.intransit;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public enum InTransitTransportMode {
    SEA("SEA", "海运"),
    AIR("AIR", "空运");

    private final String code;
    private final String label;

    InTransitTransportMode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static InTransitTransportMode require(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("运输方式只支持海运或空运。");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(mode -> mode.code.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("运输方式只支持海运或空运。"));
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(InTransitTransportMode::code).collect(Collectors.toList());
    }
}
