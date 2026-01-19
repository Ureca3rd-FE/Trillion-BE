package com.trillion.server.auth.controller;

import java.util.Map;

import com.sun.net.httpserver.Authenticator;
import com.trillion.server.auth.dto.AuthDto;
import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.common.exception.SuccessMessages;
import com.trillion.server.common.exception.SuccessResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.trillion.server.auth.service.AuthService;
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
    public ResponseEntity<SuccessResponse<AuthDto.RefreshTokenResponse>> refreshToken(
            @CookieValue(value = "refreshToken", required = false) String refreshToken, HttpServletResponse response){

        if(refreshToken == null || refreshToken.isEmpty()){
            throw new IllegalArgumentException(ErrorMessages.REFRESH_TOKEN_REQUIRED);
        }

        AuthDto.RefreshTokenResponse tokenDto = authService.refreshTokens(refreshToken);

        setCookie(response, "accessToken", tokenDto.accessToken(), 3600);
        setCookie(response, "refreshToken", tokenDto.refreshToken(), 604800);

        return ResponseEntity.ok(SuccessResponse.of(SuccessMessages.TOKEN_REFRESH_SUCCESS, tokenDto));
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
}

