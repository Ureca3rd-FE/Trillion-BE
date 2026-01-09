package com.trillion.server.common.exception;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ValidationErrorResponse {
    private final boolean success;
    private final String message;
    private final Map<String, String> errors;
    private final String error;
    
    public static ValidationErrorResponse of(String message, Map<String, String> errors) {
        return ValidationErrorResponse.builder()
                .success(false)
                .message(message)
                .errors(errors)
                .error("VALIDATION_ERROR")
                .build();
    }
}
