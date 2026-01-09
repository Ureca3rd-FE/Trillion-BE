package com.trillion.server.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TokenRefreshResponse {
    private final String accessToken;
    private final String refreshToken;
}
