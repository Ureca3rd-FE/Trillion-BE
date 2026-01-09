package com.trillion.server.auth.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.common.util.JwtUtil;
import com.trillion.server.users.entity.UserEntity;
import com.trillion.server.users.entity.UserEntity.UserRole;
import com.trillion.server.users.entity.UserEntity.UserStatus;
import com.trillion.server.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public Map<String, Object> processKakaoLogin(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        
        Long kakaoIdLong = (Long) attributes.get("id");
        String kakaoId = String.valueOf(kakaoIdLong);
        
        String nickname = "카카오사용자";
        String profileImageUrl = null;
        String thumbnailImageUrl = null;
        
        Object kakaoAccountObj = attributes.get("kakao_account");
        if (kakaoAccountObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> kakaoAccount = (Map<String, Object>) kakaoAccountObj;
            
            Object profileObj = kakaoAccount.get("profile");
            if (profileObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> profile = (Map<String, Object>) profileObj;
                nickname = (String) profile.getOrDefault("nickname", nickname);
                profileImageUrl = (String) profile.get("profile_image_url");
                thumbnailImageUrl = (String) profile.get("thumbnail_image_url");
            }
        }
        
        Optional<UserEntity> existingUser = userRepository.findByKakaoId(kakaoId);
        UserEntity user;
        boolean isNewUser = false;
        
        if (existingUser.isPresent()) {
            user = existingUser.get();
            user.updateProfile(nickname, profileImageUrl, thumbnailImageUrl);
            user.updateLastLoginAt();
        } else {
            user = UserEntity.builder()
                    .kakaoId(kakaoId)
                    .nickname(nickname)
                    .profileImageUrl(profileImageUrl)
                    .thumbnailImageUrl(thumbnailImageUrl)
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build();
            isNewUser = true;
        }
        
        userRepository.save(user);
        
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("kakaoId", kakaoId);
        result.put("nickname", user.getNickname());
        result.put("profileImageUrl", user.getProfileImageUrl());
        result.put("thumbnailImageUrl", user.getThumbnailImageUrl());
        result.put("role", user.getRole().name());
        result.put("isNewUser", isNewUser);
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        
        return result;
    }

    public Map<String, Object> refreshTokens(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken, "REFRESH")) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_REFRESH_TOKEN);
        }
        
        Long userId = jwtUtil.extractUserId(refreshToken);
        if (userId == null) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_REFRESH_TOKEN);
        }
        
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND));
        
        Long userIdValue = user.getId();
        String newAccessToken = jwtUtil.generateAccessToken(userIdValue, user.getRole().name());
        String newRefreshToken = jwtUtil.generateRefreshToken(userIdValue);
        
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("refreshToken", newRefreshToken);
        
        return result;
    }
}
