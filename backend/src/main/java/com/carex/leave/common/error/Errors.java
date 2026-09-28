package com.carex.leave.common.error;

import org.springframework.http.HttpStatus;

/** Factory methods for the error codes defined in implementation.md §20.1. */
public final class Errors {
    private Errors() {}

    public static ApiException businessRule(String code, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
    }

    public static ApiException invalidTransition(String currentStatus, String detail) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", detail).with("currentStatus", currentStatus);
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", detail);
    }

    public static ApiException forbidden(String code, String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, code, detail);
    }

    public static ApiException aiUnavailable(String detail) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", detail);
    }

    public static ApiException rateLimited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests, please slow down");
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }
}
