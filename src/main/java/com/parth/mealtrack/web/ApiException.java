package com.parth.mealtrack.web;

import org.springframework.http.HttpStatus;

/** Business-rule failure that maps directly to an HTTP status. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }

    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }

    public static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, message); }
}
