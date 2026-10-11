package com.trainbooking.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler providing centralized error handling across all controllers
 * with formatted JSON error responses and Thymeleaf fallback handling.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(com.trainbooking.it25100977.service.TicketDocumentException.class)
    public ResponseEntity<Map<String,Object>> handleTicketDocument(com.trainbooking.it25100977.service.TicketDocumentException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("success",false,"error","The ticket document or QR image could not be generated. Your reservation is retained; please retry from My Bookings."));
    }

    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<Map<String,Object>> handleMissingResource(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success",false,"error","The requested page or resource was not found."));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String,Object>> handleUnsupportedMethod(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("success",false,"error","This operation requires the supported request method."));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String,Object>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success",false,"error","You do not have access to this record."));
    }

    @ExceptionHandler({org.springframework.beans.InvalidPropertyException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.ServletRequestBindingException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String,Object>> handleInvalidRequest(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("success",false,"error","Invalid request. Check the submitted fields and try again."));
    }

    /**
     * Handles {@link ResourceNotFoundException} and returns HTTP 404 response.
     *
     * @param ex the exception thrown
     * @return JSON response with success=false and error message
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handles custom {@link ApiException} and returns the corresponding HTTP status.
     *
     * @param ex the API exception thrown
     * @return JSON response with success=false and custom status code error
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        log.error("API Exception [Status {}]: {}", ex.getStatusCode(), ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    /**
     * Handles validation errors from {@link jakarta.validation.Valid} annotated parameters.
     *
     * @param ex the validation exception
     * @return HTTP 400 Bad Request with field error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "Validation failed for one or more fields");
        body.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Fallback handler for all uncaught generic exceptions.
     *
     * @param ex the unexpected exception
     * @return HTTP 500 Internal Server Error response
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled internal server exception occurred: ", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("error", "Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
