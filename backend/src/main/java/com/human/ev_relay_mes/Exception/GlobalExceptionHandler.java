package com.human.ev_relay_mes.Exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(
            CustomException exception, HttpServletRequest request) {
        return build(exception.getStatus(), exception.getCode(), exception.getErrorMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorResponse> handleValidationException(
            BindException exception, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;
        return build(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage(),
                request.getRequestURI(), errors);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleInvalidType(
            Exception exception, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INVALID_TYPE_VALUE;
        return build(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        return build(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
        return build(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.RESOURCE_CONFLICT;
        return build(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage(),
                request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception for {}", request.getRequestURI(), exception);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return build(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage(),
                request.getRequestURI(), null);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String code, String message, String path, Map<String, String> errors) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .code(code)
                .message(message)
                .path(path)
                .errors(errors == null || errors.isEmpty() ? null : errors)
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
