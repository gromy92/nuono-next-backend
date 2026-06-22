package com.nuono.next.competitoranalysis.noon;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class NoonProductCodeSupport {
    private static final Pattern NOON_CODE_PATTERN = Pattern.compile("(?i)(^|[^A-Z0-9])([ZN][A-Z0-9]{7,30})(?=$|[^A-Z0-9])");

    private NoonProductCodeSupport() {
    }

    public static Optional<String> extractFirst(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        Matcher matcher = NOON_CODE_PATTERN.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(normalize(matcher.group(2)));
    }

    public static Optional<String> codeType(String noonProductCode) {
        String normalized = normalize(noonProductCode);
        if (!StringUtils.hasText(normalized)) {
            return Optional.empty();
        }
        if (normalized.charAt(0) == 'Z') {
            return Optional.of("Z_CODE");
        }
        if (normalized.charAt(0) == 'N') {
            return Optional.of("N_CODE");
        }
        return Optional.empty();
    }

    public static String normalize(String noonProductCode) {
        return StringUtils.hasText(noonProductCode)
                ? noonProductCode.trim().toUpperCase(Locale.ROOT)
                : null;
    }
}
