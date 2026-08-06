package com.nuono.next.datapull.runtime;

import java.util.Objects;
import java.util.regex.Pattern;

public final class SanitizedCode {

    private static final int MAX_LENGTH = 80;
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");

    private SanitizedCode() {
    }

    public static String require(String code) {
        String nonNull = Objects.requireNonNull(code, "sanitizedCode");
        if (!nonNull.equals(nonNull.trim())
                || nonNull.length() > MAX_LENGTH
                || !SAFE.matcher(nonNull).matches()) {
            throw new IllegalArgumentException(
                    "sanitizedCode must contain only a safe identifier and be at most " + MAX_LENGTH + " characters"
            );
        }
        return nonNull;
    }
}
