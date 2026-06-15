package com.nuono.next.product.noon;

public class NoonProductError {
    private final NoonProductErrorCode code;
    private final boolean retryable;
    private final String message;

    public NoonProductError(NoonProductErrorCode code, boolean retryable, String message) {
        this.code = code;
        this.retryable = retryable;
        this.message = message;
    }

    public NoonProductErrorCode getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getMessage() {
        return message;
    }
}
