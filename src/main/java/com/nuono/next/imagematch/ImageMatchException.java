package com.nuono.next.imagematch;

import org.springframework.http.HttpStatus;

public class ImageMatchException extends RuntimeException {

    private final HttpStatus status;

    public ImageMatchException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
