package com.nuono.next.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiProblemExceptionHandler {

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ApiProblemResponse> handle(ApiProblemException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(new ApiProblemResponse(exception));
    }
}
