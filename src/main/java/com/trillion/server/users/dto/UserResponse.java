package com.trillion.server.users.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {
    private final Long userId;
    private final String kakaoId;
    private final String nickname;
    private final String profileImageUrl;
    private final String thumbnailImageUrl;
    private final String role;
    private final String status;
    private final LocalDateTime lastLoginAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
