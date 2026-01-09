package com.trillion.server.common.exception;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.persistence.EntityNotFoundException;

@Hidden
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("IllegalArgumentException 발생: {}", e.getMessage(), e);
        String safeMessage = getSafeMessage(e.getMessage());
        ErrorResponse response = ErrorResponse.of(safeMessage, "BAD_REQUEST");
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFoundException(EntityNotFoundException e) {
        logger.warn("EntityNotFoundException 발생: {}", e.getMessage(), e);
        ErrorResponse response = ErrorResponse.of(ErrorMessages.NOT_FOUND, "NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        
        e.getBindingResult().getAllErrors().forEach(error -> {
            String errorMessage = error.getDefaultMessage();
            if (error instanceof FieldError) {
                FieldError fieldError = (FieldError) error;
                errors.put(fieldError.getField(), errorMessage);
            } else if (error instanceof ObjectError) {
                ObjectError objectError = (ObjectError) error;
                String objectName = objectError.getObjectName();
                errors.put(objectName, errorMessage);
            } else {
                errors.put("error", errorMessage);
            }
        });
        
        logger.warn("입력값 검증 실패: {}", errors);
        
        ValidationErrorResponse response = ValidationErrorResponse.of(ErrorMessages.VALIDATION_FAILED, errors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        logger.error("처리되지 않은 예외 발생", e);
        ErrorResponse response = ErrorResponse.of(ErrorMessages.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String getSafeMessage(String exceptionMessage) {
        if (exceptionMessage == null) {
            return ErrorMessages.BAD_REQUEST;
        }

        String message = exceptionMessage.trim();
        
        if (message.contains(ErrorMessages.AUTH_TOKEN_REQUIRED) || 
            message.contains(ErrorMessages.INVALID_TOKEN) ||
            message.contains(ErrorMessages.INVALID_REFRESH_TOKEN) ||
            message.contains(ErrorMessages.REFRESH_TOKEN_REQUIRED)) {
            return message;
        }
        
        if (message.contains(ErrorMessages.USER_NOT_FOUND)) {
            return ErrorMessages.USER_NOT_FOUND;
        }
        
        if (message.contains(ErrorMessages.USER_ALREADY_DELETED)) {
            return ErrorMessages.USER_ALREADY_DELETED;
        }
        
        if (message.contains(ErrorMessages.USER_ID_REQUIRED)) {
            return ErrorMessages.USER_ID_REQUIRED;
        }
        
        if (message.contains("탈퇴한 사용자")) {
            return ErrorMessages.USER_ALREADY_DELETED;
        }
        
        if (message.contains("로그인")) {
            return ErrorMessages.LOGIN_FAILED;
        }
        
        return ErrorMessages.BAD_REQUEST;
    }
}
