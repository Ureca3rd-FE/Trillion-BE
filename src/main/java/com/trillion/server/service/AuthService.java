package com.trillion.server.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    
    public Map<String, Object> getKakaoUserInfo(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        Map<String, Object> result = new HashMap<>();
        
        Long kakaoIdLong = (Long) attributes.get("id");
        String kakaoId = String.valueOf(kakaoIdLong);
        result.put("kakaoId", kakaoId);
        result.put("id", kakaoIdLong);
        
        Object kakaoAccountObj = attributes.get("kakao_account");
        if (!(kakaoAccountObj instanceof Map)) {
            result.put("email", "");
            result.put("nickname", "카카오사용자");
            result.put("profileImageUrl", "");
            result.put("thumbnailImageUrl", "");
            result.put("rawAttributes", attributes);
            return result;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> kakaoAccount = (Map<String, Object>) kakaoAccountObj;
        
        String email = (String) kakaoAccount.get("email");
        result.put("email", email != null ? email : "");
        
        Object profileObj = kakaoAccount.get("profile");
        final String defaultNickname = "카카오사용자";
        String nickname = defaultNickname;
        String profileImageUrl = null;
        String thumbnailImageUrl = null;
        
        if (profileObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) profileObj;
            nickname = (String) profile.getOrDefault("nickname", defaultNickname);
            profileImageUrl = (String) profile.get("profile_image_url");
            thumbnailImageUrl = (String) profile.get("thumbnail_image_url");
        }
        
        result.put("nickname", nickname);
        result.put("profileImageUrl", profileImageUrl != null ? profileImageUrl : "");
        result.put("thumbnailImageUrl", thumbnailImageUrl != null ? thumbnailImageUrl : "");
        result.put("rawAttributes", attributes);
        
        return result;
    }
}
