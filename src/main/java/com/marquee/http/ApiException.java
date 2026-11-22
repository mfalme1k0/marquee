package com.marquee.http;

import java.util.List;

/** An expected HTTP-layer failure, such as malformed JSON or a bad query value. */
public final class ApiException extends RuntimeException {
    private final int statusCode;
    private final List<String> details;

    public ApiException(int statusCode, String message) {
        this(statusCode, message, List.of());
    }

    public ApiException(int statusCode, String message, List<String> details) {
        super(message);
        if (statusCode < 400 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be an error status");
        }
        this.statusCode = statusCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public int statusCode() {
        return statusCode;
    }

    public List<String> details() {
        return details;
    }
}
