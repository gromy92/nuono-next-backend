package com.nuono.next.intransit;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public enum InTransitDestination {
    RUH("RUH", "利雅得"),
    DB("DB", "迪拜");

    private final String code;
    private final String label;

    InTransitDestination(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static InTransitDestination require(String value) {
        InTransitDestination destination = match(value);
        if (destination == null) {
            throw new IllegalArgumentException("目的地只支持 RUH 利雅得或 DB 迪拜。");
        }
        return destination;
    }

    public static InTransitDestination infer(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            InTransitDestination direct = directMatch(value);
            if (direct != null) {
                return direct;
            }
        }
        for (String value : values) {
            InTransitDestination legacy = legacyMatch(value);
            if (legacy != null) {
                return legacy;
            }
        }
        return null;
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(InTransitDestination::code).collect(Collectors.toList());
    }

    private static InTransitDestination match(String value) {
        InTransitDestination direct = directMatch(value);
        return direct == null ? legacyMatch(value) : direct;
    }

    private static InTransitDestination directMatch(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if ("RUH".equals(normalized)
                || "RIYADH".equals(normalized)
                || "SA".equals(normalized)
                || "SAUDIARABIA".equals(normalized)
                || "利雅得".equals(value.trim())) {
            return RUH;
        }
        if ("DB".equals(normalized)
                || "AE".equals(normalized)
                || "DXB".equals(normalized)
                || "DUBAI".equals(normalized)
                || "迪拜".equals(value.trim())) {
            return DB;
        }
        return null;
    }

    private static InTransitDestination legacyMatch(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.contains("RUH")
                || normalized.contains("KSA")
                || normalized.contains("SAUDI")
                || normalized.endsWith("NSA")
                || normalized.contains("FBNRUH")) {
            return RUH;
        }
        if (normalized.contains("DXB")
                || normalized.contains("UAE")
                || normalized.endsWith("NAE")
                || normalized.contains("FBNDXB")
                || normalized.contains("STOREJED01")) {
            return DB;
        }
        return null;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s_\\-/]+", "")
                : null;
    }
}
