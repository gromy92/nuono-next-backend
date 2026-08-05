package com.nuono.next.noonpull;

/** Sanitized DP-06 provider contract failure. */
final class NoonAdvertisingContractException extends RuntimeException {

    private final String sanitizedCode;

    NoonAdvertisingContractException(String sanitizedCode) {
        super(sanitizedCode);
        this.sanitizedCode = sanitizedCode;
    }

    NoonAdvertisingContractException(String sanitizedCode, Throwable cause) {
        super(sanitizedCode, cause);
        this.sanitizedCode = sanitizedCode;
    }

    String getSanitizedCode() {
        return sanitizedCode;
    }
}
