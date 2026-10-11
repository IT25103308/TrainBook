package com.trainbooking.exception;

import lombok.Getter;

/**
 * Custom API runtime exception carrying an HTTP status code and a descriptive message.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Getter
public class ApiException extends RuntimeException {

    private final int statusCode;
    private final String message;

    /**
     * Constructs a new ApiException.
     *
     * @param statusCode the HTTP status code representing the error
     * @param message the detail error message
     */
    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.message = message;
    }

    /**
     * Constructs a new ApiException with a cause.
     *
     * @param statusCode the HTTP status code representing the error
     * @param message the detail error message
     * @param cause the root cause
     */
    public ApiException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.message = message;
    }
}
