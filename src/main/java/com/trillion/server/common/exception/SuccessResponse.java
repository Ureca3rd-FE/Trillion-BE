package com.trillion.server.common.exception;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SuccessResponse<T> {
    private final boolean success;
    private final String message;
    private final T data;
    
    public static <T> SuccessResponse<T> of(T data) {
        return SuccessResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }
    
    public static <T> SuccessResponse<T> of(String message, T data) {
        return SuccessResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }
    
    public static SuccessResponse<Void> of(String message) {
        return SuccessResponse.<Void>builder()
                .success(true)
                .message(message)
                .build();
    }
}
