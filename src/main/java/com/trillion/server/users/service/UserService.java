package com.trillion.server.users.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.common.util.JwtUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.trillion.server.users.entity.UserEntity;
import com.trillion.server.users.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String kakaoClientId;

    @Value("${spring.security.oauth2.client.registration.kakao.client-secret}")
    private String kakaoClientSecret;

    @Transactional(readOnly = true)
    public UserEntity getCurrentUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException(ErrorMessages.USER_ID_REQUIRED);
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(ErrorMessages.USER_NOT_FOUND));
    }

    @Transactional
    public void deleteAccount(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException(ErrorMessages.USER_ID_REQUIRED);
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(ErrorMessages.USER_NOT_FOUND));

        String socialRefreshToken = user.getSocialRefreshToken();
        if(socialRefreshToken != null){
            try {
                String newAccessToken = refreshKakaoAccessToken(socialRefreshToken);
                unlinkKakaoUser(newAccessToken);
            } catch (Exception e) {
                log.error("카카오 연결 끊기 실패 (User ID: {}): {}", userId, e.getMessage());
            }
        }
        userRepository.delete(user);
    }

    @Transactional
    public void signUpUser(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.USER_NOT_FOUND));

        user.upgradeToUser();
    }

    private String refreshKakaoAccessToken(String refreshToken) throws JsonProcessingException{
        String tokenUrl = "https://kauth.kakao.com/oauth/token";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", kakaoClientId);
        body.add("client_secret", kakaoClientSecret);
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("access_token").asText();
    }

    private void unlinkKakaoUser(String accessToken){
        String unlinkUrl = "https://kapi.kakao.com/v1/user/unlink";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>("", headers);

        restTemplate.postForEntity(unlinkUrl, entity, String.class);
    }
}
