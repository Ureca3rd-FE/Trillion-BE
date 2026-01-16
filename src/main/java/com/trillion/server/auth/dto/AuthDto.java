package com.trillion.server.auth.dto;

import lombok.Builder;

public class AuthDto {
    @Builder
    public record LoginResponse(
            Long userId,
            String accessToken,
            String refreshToken,
            String nickname
    ) {}

    @Builder
    public record RefreshTokenResponse(
        String accessToken,
        String refreshToken
    ) {}
}
