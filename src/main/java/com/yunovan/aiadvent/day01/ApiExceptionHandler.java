package com.yunovan.aiadvent.day01;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorBody(String error) {
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> handle(ResponseStatusException ex) {
        String message = ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorBody(message));
    }
}
