package com.trillion.server.users.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trillion.server.common.exception.SuccessResponse;
import com.trillion.server.common.util.JwtUtil;
import com.trillion.server.users.entity.UserEntity;
import com.trillion.server.users.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "사용자", description = "사용자 정보 관리 API")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 정보를 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                        {
                          "success": true,
                          "data": {
                            "userId": 1,
                            "kakaoId": "123456789",
                            "nickname": "홍길동",
                            "profileImageUrl": "https://k.kakaocdn.net/dn/example/profile.jpg",
                            "thumbnailImageUrl": "https://k.kakaocdn.net/dn/example/thumb.jpg",
                            "role": "USER",
                            "status": "ACTIVE",
                            "lastLoginAt": "2024-01-08T10:30:00",
                            "createdAt": "2024-01-01T00:00:00",
                            "updatedAt": "2024-01-08T10:30:00"
                          }
                        }
                        """
                )
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> getCurrentUser(
            @CookieValue(value = "accessToken", required = false) String accessToken) {
        
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IllegalArgumentException("인증 토큰이 필요합니다.");
        }
        
        if (!jwtUtil.validateToken(accessToken, "ACCESS")) {
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
        }
        
        Long userId = jwtUtil.extractUserId(accessToken);
        UserEntity user = userService.getCurrentUser(userId);
        
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", user.getId());
        userData.put("kakaoId", user.getKakaoId());
        userData.put("nickname", user.getNickname());
        userData.put("profileImageUrl", user.getProfileImageUrl());
        userData.put("thumbnailImageUrl", user.getThumbnailImageUrl());
        userData.put("role", user.getRole().name());
        userData.put("status", user.getStatus().name());
        userData.put("lastLoginAt", user.getLastLoginAt());
        userData.put("createdAt", user.getCreatedAt());
        userData.put("updatedAt", user.getUpdatedAt());
        
        return ResponseEntity.ok(SuccessResponse.of(userData));
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자의 계정을 탈퇴 처리합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "회원 탈퇴 성공"),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 토큰 또는 이미 탈퇴한 사용자"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @DeleteMapping("/me")
    public ResponseEntity<SuccessResponse<Void>> deleteAccount(
            @CookieValue(value = "accessToken", required = false) String accessToken,
            HttpServletResponse response) {
        
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IllegalArgumentException("인증 토큰이 필요합니다.");
        }
        
        if (!jwtUtil.validateToken(accessToken, "ACCESS")) {
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
        }
        
        Long userId = jwtUtil.extractUserId(accessToken);
        userService.deleteAccount(userId);
        
        deleteTokenCookies(response);
        
        return ResponseEntity.ok(SuccessResponse.of("회원 탈퇴가 완료되었습니다."));
    }
    
    private void deleteTokenCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", "accessToken=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
        response.addHeader("Set-Cookie", "refreshToken=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
    }
}
