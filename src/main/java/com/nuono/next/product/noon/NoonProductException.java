package com.nuono.next.product.noon;

public class NoonProductException extends IllegalStateException {
    private final NoonProductError error;

    public NoonProductException(NoonProductError error, Throwable cause) {
        super(error == null ? null : error.getMessage(), cause);
        this.error = error;
    }

    public NoonProductError getError() {
        return error;
    }

    public NoonProductErrorCode getCode() {
        return error == null ? NoonProductErrorCode.NOON_REQUEST_FAILED : error.getCode();
    }

    public boolean isRetryable() {
        return error != null && error.isRetryable();
    }
}
