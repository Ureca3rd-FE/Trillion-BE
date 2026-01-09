package com.trillion.server.auth.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trillion.server.auth.dto.LoginResponse;
import com.trillion.server.auth.dto.TokenRefreshResponse;
import com.trillion.server.auth.entity.RefreshToken;
import com.trillion.server.auth.repository.RefreshTokenRepository;
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

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public LoginResponse processKakaoLogin(OAuth2User oAuth2User) {
        if (oAuth2User == null) {
            throw new IllegalArgumentException("OAuth2User가 null입니다.");
        }
        
        Map<String, Object> attributes = oAuth2User.getAttributes();
        if (attributes == null) {
            throw new IllegalArgumentException("OAuth2User attributes가 null입니다.");
        }
        
        Object idObj = attributes.get("id");
        if (idObj == null) {
            throw new IllegalArgumentException("카카오 ID가 없습니다.");
        }
        
        Long kakaoIdLong;
        if (idObj instanceof Long longValue) {
            kakaoIdLong = longValue;
        } else if (idObj instanceof Number number) {
            kakaoIdLong = number.longValue();
        } else {
            throw new IllegalArgumentException("카카오 ID 형식이 올바르지 않습니다.");
        }
        
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
        
        if (existingUser.isPresent()) {
            user = existingUser.get();
            if (user.getStatus() == UserStatus.DELETED) {
                logger.warn("탈퇴한 사용자가 로그인 시도: userId={}, kakaoId={}", user.getId(), kakaoId);
                throw new IllegalArgumentException("탈퇴한 사용자는 로그인할 수 없습니다.");
            }
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
        }
        
        UserEntity savedUser = userRepository.save(user);
        
        String accessToken = jwtUtil.generateAccessToken(savedUser.getId(), savedUser.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(savedUser.getId());
        
        refreshTokenRepository.findByUserId(savedUser.getId()).ifPresent(refreshTokenRepository::delete);
        
        LocalDateTime expiresAt = jwtUtil.getTokenExpirationAsLocalDateTime(refreshToken);
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .userId(savedUser.getId())
                .token(refreshToken)
                .expiresAt(expiresAt)
                .build();
        refreshTokenRepository.save(refreshTokenEntity);
        
        return LoginResponse.builder()
                .userId(savedUser.getId())
                .kakaoId(kakaoId)
                .nickname(savedUser.getNickname())
                .profileImageUrl(savedUser.getProfileImageUrl())
                .thumbnailImageUrl(savedUser.getThumbnailImageUrl())
                .role(savedUser.getRole().name())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional
    public TokenRefreshResponse refreshTokens(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken, "REFRESH")) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_REFRESH_TOKEN);
        }
        
        RefreshToken existingToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.INVALID_REFRESH_TOKEN));
        
        if (existingToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(existingToken);
            throw new IllegalArgumentException(ErrorMessages.INVALID_REFRESH_TOKEN);
        }
        
        Long userId = jwtUtil.extractUserId(refreshToken);
        if (!userId.equals(existingToken.getUserId())) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_REFRESH_TOKEN);
        }
        
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND));
        
        if (user.getStatus() == UserStatus.DELETED) {
            logger.warn("탈퇴한 사용자의 토큰 갱신 시도: userId={}", userId);
            refreshTokenRepository.delete(existingToken);
            throw new IllegalArgumentException("탈퇴한 사용자의 토큰은 갱신할 수 없습니다.");
        }
        
        if (user.getStatus() != UserStatus.ACTIVE) {
            logger.warn("비활성 사용자의 토큰 갱신 시도: userId={}, status={}", userId, user.getStatus());
            refreshTokenRepository.delete(existingToken);
            throw new IllegalArgumentException("비활성 사용자의 토큰은 갱신할 수 없습니다.");
        }
        
        refreshTokenRepository.delete(existingToken);
        
        Long userIdValue = user.getId();
        String newAccessToken = jwtUtil.generateAccessToken(userIdValue, user.getRole().name());
        String newRefreshToken = jwtUtil.generateRefreshToken(userIdValue);
        
        LocalDateTime expiresAt = jwtUtil.getTokenExpirationAsLocalDateTime(newRefreshToken);
        RefreshToken newRefreshTokenEntity = RefreshToken.builder()
                .userId(userIdValue)
                .token(newRefreshToken)
                .expiresAt(expiresAt)
                .build();
        refreshTokenRepository.save(newRefreshTokenEntity);
        
        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }
    
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isEmpty()) {
            refreshTokenRepository.findByToken(refreshToken)
                    .ifPresent(refreshTokenRepository::delete);
        }
    }
    
    @Transactional
    public void logoutByUserId(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
