package com.nuono.next.mobile;

public class MobileApiException extends RuntimeException {

    private final int code;

    public MobileApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
