package com.nuono.next.permission.access;

public class BusinessAccessDeniedException extends RuntimeException {

    public BusinessAccessDeniedException(String message) {
        super(message);
    }
}
