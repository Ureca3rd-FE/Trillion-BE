package com.trillion.server.auth.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trillion.server.auth.service.AuthService;
import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.common.exception.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "JWT 토큰 관리 API")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "JWT 토큰 갱신", description = "Refresh Token을 사용하여 새로운 Access Token과 Refresh Token을 발급합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 리프레시 토큰")
    })
    @PostMapping("/refresh")
    public ResponseEntity<SuccessResponse<Map<String, Object>>> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessages.REFRESH_TOKEN_REQUIRED);
        }
        
        Map<String, Object> tokenData = authService.refreshTokens(refreshToken);
        return ResponseEntity.ok(SuccessResponse.of(tokenData));
    }

    @Operation(summary = "로그아웃", description = "현재 로그인한 사용자를 로그아웃 처리하고 토큰 쿠키를 삭제합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    })
    @PostMapping("/logout")
    public ResponseEntity<SuccessResponse<Void>> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        
        if (refreshToken != null && !refreshToken.isEmpty()) {
            authService.logout(refreshToken);
        }
        
        deleteTokenCookies(response);
        
        return ResponseEntity.ok(SuccessResponse.of("로그아웃이 완료되었습니다."));
    }
    
    private void deleteTokenCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", "accessToken=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
        response.addHeader("Set-Cookie", "refreshToken=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
        response.addHeader("Set-Cookie", "isSignin=; Path=/; Max-Age=0; SameSite=Lax");
    }
}
