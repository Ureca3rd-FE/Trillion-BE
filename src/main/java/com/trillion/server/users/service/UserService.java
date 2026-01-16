package com.trillion.server.users.service;

import com.trillion.server.common.util.JwtUtil;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.trillion.server.users.entity.UserEntity;
import com.trillion.server.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public UserEntity getCurrentUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID가 필요합니다.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public void deleteAccount(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID가 필요합니다.");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("사용자를 찾을 수 없습니다."));

        userRepository.save(user);
    }

    @Transactional
    public Map<String, Object> processKakaoLogin(OAuth2User oAuth2User){
        Map<String, Object> parsing = oAuth2User.getAttributes();
        String kakaoId = String.valueOf(parsing.get("id"));
        Map<String, Object> kakaoAccount = (Map<String, Object>) parsing.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
        String nickname = (String) profile.get("nickname");

        UserEntity user = userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> {
                    UserEntity newUser = UserEntity.builder()
                            .kakaoId(kakaoId)
                            .nickname(nickname)
                            .build();
                    return userRepository.save(newUser);
                });

        String accessToken = jwtUtil.generateAccessToken(user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        user.updateRefreshToken(refreshToken);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        return result;
    }
}
