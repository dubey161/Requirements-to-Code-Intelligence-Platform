package com.engineeringproductivity.platform.common.api;

import com.engineeringproductivity.platform.codegen.application.CompileValidationService;
import com.engineeringproductivity.platform.requirement.analysis.application.AnalysisFailedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException exception) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(DisabledException.class)
    ResponseEntity<ApiError> handleDisabled(DisabledException exception) {
        return error(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(AnalysisFailedException.class)
    ResponseEntity<ApiError> handleAnalysisFailed(AnalysisFailedException exception) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "ANALYSIS_FAILED", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(CompileValidationService.CompileValidationException.class)
    ResponseEntity<ApiError> handleCompileValidation(CompileValidationService.CompileValidationException exception) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "COMPILE_VALIDATION_FAILED", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> handleIllegalState(IllegalStateException exception) {
        return error(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ResourceConflictException.class)
    ResponseEntity<ApiError> handleConflict(ResourceConflictException exception) {
        return error(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), exception);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred while processing " + request.getRequestURI(),
                Map.of()
        );
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            Map<String, String> fieldErrors
    ) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), code, message, fieldErrors));
    }
}
