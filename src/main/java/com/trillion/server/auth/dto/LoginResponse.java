package com.trillion.server.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private final Long userId;
    private final String kakaoId;
    private final String nickname;
    private final String profileImageUrl;
    private final String thumbnailImageUrl;
    private final String role;
    private final String accessToken;
    private final String refreshToken;
}
