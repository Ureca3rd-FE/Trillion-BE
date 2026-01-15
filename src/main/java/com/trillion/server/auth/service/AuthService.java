package com.trillion.server.auth.service;

import java.util.HashMap;
import java.util.Map;

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
    public Map<String, Object> processKakaoLogin(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String kakaoId = String.valueOf(attributes.get("id"));
        String nickname = "kakaoUser";
        
        Object kakaoAccountObj = attributes.get("kakao_account");
        if (kakaoAccountObj instanceof Map) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) kakaoAccountObj;
            Object profileObj = kakaoAccount.get("profile");

            if (profileObj instanceof Map) {
                Map<String, Object> profile = (Map<String, Object>) profileObj;
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
        
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("kakaoId", kakaoId);
        result.put("nickname", user.getNickname());
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        
        return result;
    }

    @Transactional
    public Map<String, Object> refreshTokens(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken, "REFRESH")) {
            throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.");
        }
        
        Long userId = jwtUtil.extractUserId(refreshToken);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("시용자를 찾을 수 없습니다."));
        if(user.getRefreshToken() == null || !user.getRefreshToken().equals(refreshToken)){
            throw new IllegalArgumentException("유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인 해주세요.");
        }

        String newAccessToken = jwtUtil.generateAccessToken(userId);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);
        user.updateRefreshToken(newRefreshToken);
        
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("refreshToken", newRefreshToken);
        
        return result;
    }

    @Transactional
    public void logout(Long userId){
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        user.updateRefreshToken(null);
        userRepository.save(user);
    }
}
