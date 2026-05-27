package com.nuono.next.productselection;

public class Ali1688PluginAssignmentException extends RuntimeException {

    private final String errorCode;

    public Ali1688PluginAssignmentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
