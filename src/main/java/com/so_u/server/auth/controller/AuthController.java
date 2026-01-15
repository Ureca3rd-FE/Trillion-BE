package com.so_u.server.auth.controller;

import java.util.Map;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.so_u.server.auth.service.AuthService;
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
        @ApiResponse(responseCode = "400", description = "유효하지 않은 토큰")
    })
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @CookieValue(value = "refreshToken", required = false) String refreshToken, HttpServletResponse response){

        if(refreshToken == null || refreshToken.isEmpty()){
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "리프레시 토큰이 쿠키에 없습니다."));
        }

        try{
            Map<String, Object> tokens = authService.refreshTokens(refreshToken);

            String newAccessToken = (String) tokens.get("accessToken");
            String newRefreshToken = (String) tokens.get("refreshToken");

            setCookie(response, "accessToken", newAccessToken, 3600);
            setCookie(response, "refreshToken", newRefreshToken, 604800);

            return ResponseEntity.ok().body(Map.of("success", true, "message", "토큰이 갱신되었습니다."));
        } catch (IllegalArgumentException e){
            deleteCookie(response, "accessToken");
            deleteCookie(response, "refreshToken");
            return ResponseEntity.status(401).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private void setCookie(HttpServletResponse response, String name, String value, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .path("/")
                .sameSite("Lax")
                .httpOnly(true)
                .secure(false)
                .maxAge(maxAge)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void deleteCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .path("/")
                .sameSite("Lax")
                .httpOnly(true)
                .secure(false)
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}

