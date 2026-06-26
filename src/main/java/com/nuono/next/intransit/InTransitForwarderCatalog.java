package com.nuono.next.intransit;

import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

public final class InTransitForwarderCatalog {

    private static final List<CanonicalForwarder> MANAGED = List.of(
            new CanonicalForwarder("QIKE", "启客"),
            new CanonicalForwarder("YITONG", "易通"),
            new CanonicalForwarder("YITE", "义特")
    );

    private InTransitForwarderCatalog() {
    }

    public static List<CanonicalForwarder> managedForwarders() {
        return MANAGED;
    }

    public static CanonicalForwarder require(String code, String name) {
        CanonicalForwarder forwarder = match(code, name);
        if (forwarder == null) {
            throw new IllegalArgumentException("物流方只支持启客、易通、义特。");
        }
        return forwarder;
    }

    public static CanonicalForwarder match(String code, String name) {
        CanonicalForwarder byCode = matchOne(code);
        return byCode == null ? matchOne(name) : byCode;
    }

    private static CanonicalForwarder matchOne(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.contains("启客") || normalized.contains("qike")) {
            return MANAGED.get(0);
        }
        if (normalized.contains("易通") || normalized.contains("yitong") || "et".equals(normalized)) {
            return MANAGED.get(1);
        }
        if (normalized.contains("义特") || normalized.contains("yite")) {
            return MANAGED.get(2);
        }
        for (CanonicalForwarder forwarder : MANAGED) {
            if (forwarder.code().toLowerCase(Locale.ROOT).equals(normalized)) {
                return forwarder;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-/]+", "")
                : null;
    }

    public static final class CanonicalForwarder {
        private final String code;
        private final String name;

        private CanonicalForwarder(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String code() {
            return code;
        }

        public String name() {
            return name;
        }
    }
}
