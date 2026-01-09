package com.trillion.server.common.exception;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponse {
    private final boolean success;
    private final String message;
    private final String error;
    
    public static ErrorResponse of(String message, String error) {
        return ErrorResponse.builder()
                .success(false)
                .message(message)
                .error(error)
                .build();
    }
}
