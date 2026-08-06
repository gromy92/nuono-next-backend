package com.nuono.next.noon;

import java.util.Objects;
import java.util.regex.Pattern;

/** Deterministic binary-transfer contract failure that must never enter transient backoff. */
public class NoonBinaryDownloadContractException extends IllegalStateException {
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,79}");
    private final String code;

    public NoonBinaryDownloadContractException(String code) {
        super(requireCode(code));
        this.code = code;
    }

    public final String getCode() {
        return code;
    }

    private static String requireCode(String code) {
        String value = Objects.requireNonNull(code, "code");
        if (!SAFE_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("binary download contract code is invalid");
        }
        return value;
    }
}
