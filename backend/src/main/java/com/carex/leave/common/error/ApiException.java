package com.carex.leave.common.error;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/** Base for all domain/API exceptions: carries HTTP status + stable machine-readable code. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> extra = new LinkedHashMap<>();

    public ApiException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public ApiException with(String key, Object value) {
        extra.put(key, value);
        return this;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, Object> getExtra() { return extra; }
}
