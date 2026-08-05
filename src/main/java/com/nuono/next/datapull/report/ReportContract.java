package com.nuono.next.datapull.report;

import java.util.Objects;
import java.util.regex.Pattern;

final class ReportContract {

    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");

    private ReportContract() {
    }

    static String requireIdentity(String value, String name) {
        String nonNull = Objects.requireNonNull(value, name);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim())) {
            throw new IllegalArgumentException(name + " must be a non-blank stable identity");
        }
        return nonNull;
    }

    static String optionalIdentity(String value, String name) {
        return value == null ? null : requireIdentity(value, name);
    }

    static String requireSafeCode(String value, String name) {
        String code = requireIdentity(value, name);
        if (code.length() > 80 || !SAFE_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException(name + " must be a safe identifier of at most 80 characters");
        }
        return code;
    }
}
