package com.spendwise.common.exception;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(com.spendwise.auth.integration.EmailDeliveryException.class)
    ResponseEntity<ApiError> handleEmailDeliveryFailure(
            com.spendwise.auth.integration.EmailDeliveryException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(
                "EMAIL_DELIVERY_UNAVAILABLE",
                "We could not send the verification email. Please try again later.",
                Map.of()));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException exception) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.getStatus());
        if (exception instanceof com.spendwise.common.security.Channel3UserRateLimiter.Channel3RateLimitExceededException limit) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(limit.getRetryAfterSeconds()));
        }
        if (exception instanceof com.spendwise.common.security.GeminiUserRateLimiter.GeminiRateLimitExceededException limit) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(limit.getRetryAfterSeconds()));
        }
        return response.body(new ApiError(exception.getCode(), exception.getMessage(), exception.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalidRequest(MethodArgumentNotValidException exception) {
        Map<String, Object> details = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                        (first, ignored) -> first));
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_FAILED", "Request validation failed.", details));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", "Request validation failed.", Map.of()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", "Request validation failed.", Map.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_REQUEST", "Request body is missing or malformed.", Map.of()));
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> handleMissingOrInvalidParameter(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_REQUEST", "A required request value is missing or invalid.", Map.of()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleUnsupportedMethod(HttpRequestMethodNotSupportedException exception) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (exception.getSupportedHttpMethods() != null) {
            response.header(HttpHeaders.ALLOW, exception.getSupportedHttpMethods().stream()
                    .map(org.springframework.http.HttpMethod::name)
                    .collect(Collectors.joining(", ")));
        }
        return response.body(new ApiError(
                "METHOD_NOT_ALLOWED", "The HTTP method is not supported for this route.", Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("DATA_CONFLICT", "The request conflicts with existing data.", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled API exception type {}", exception.getClass().getName());
        return ResponseEntity.internalServerError()
                .body(new ApiError("INTERNAL_ERROR", "An unexpected server error occurred.", Map.of()));
    }

    public record ApiError(String code, String message, Map<String, Object> details) {
    }
}
