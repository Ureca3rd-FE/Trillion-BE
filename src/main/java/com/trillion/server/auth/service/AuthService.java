package com.trillion.server.auth.service;

import java.util.HashMap;
import java.util.Map;

import com.trillion.server.auth.dto.AuthDto;
import com.trillion.server.common.exception.ErrorMessages;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trillion.server.common.util.JwtUtil;
import com.trillion.server.users.entity.UserEntity;
import com.trillion.server.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthDto.LoginResponse processKakaoLogin(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String kakaoId = String.valueOf(attributes.get("id"));
        String nickname = "kakaoUser";
        
        Object kakaoAccountObj = attributes.get("kakao_account");
        if (kakaoAccountObj instanceof Map<?, ?> kakaoAccount) {
            Object profileObj = kakaoAccount.get("profile");

            if (profileObj instanceof Map<?, ?> profile) {
                nickname = (String) profile.getOrDefault("nickname", nickname);
            }
        }

        String finalNickname = nickname;
        
        UserEntity user = userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                                .kakaoId(kakaoId)
                                .nickname(finalNickname)
                                .build()));

        String accessToken = jwtUtil.generateAccessToken(user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        user.updateRefreshToken(refreshToken);
        
        user.updateRefreshToken(refreshToken);
        
        return AuthDto.LoginResponse.builder()
                .userId(user.getId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .nickname(user.getNickname())
                .build();
    }

    @Transactional
    public AuthDto.RefreshTokenResponse refreshTokens(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken, "REFRESH")) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_REFRESH_TOKEN);
        }
        
        Long userId = jwtUtil.extractUserId(refreshToken);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.USER_NOT_FOUND));
        
        if(user.getRefreshToken() == null || !user.getRefreshToken().equals(refreshToken)){
            throw new IllegalArgumentException(ErrorMessages.INVALID_REFRESH_TOKEN);
        }

        String newAccessToken = jwtUtil.generateAccessToken(userId);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);
        user.updateRefreshToken(newRefreshToken);
        
        return AuthDto.RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Transactional
    public void logout(Long userId){
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.USER_NOT_FOUND));

        user.updateRefreshToken(null);
    }
}
