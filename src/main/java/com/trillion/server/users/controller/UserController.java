package com.trillion.server.users.controller;

import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.common.exception.SuccessMessages;
import com.trillion.server.common.exception.SuccessResponse;
import com.trillion.server.users.dto.UserDto;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.trillion.server.users.entity.UserEntity;
import com.trillion.server.common.util.JwtUtil;
import com.trillion.server.users.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/users")
@RequiredArgsConstructor
@Tag(name = "사용자", description = "사용자 정보 관리 API")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 정보를 조회")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = SuccessResponse.class))
        ),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })

    @GetMapping("/member/profile")
    public ResponseEntity<SuccessResponse<UserDto.UserProfileResponse>> getCurrentUser(
            @CookieValue(value = "accessToken", required = false) String accessToken) {

        validateToken(accessToken);

        Long userId = jwtUtil.extractUserId(accessToken);
        UserEntity user = userService.getCurrentUser(userId);
        
        return ResponseEntity.ok(SuccessResponse.of(
                SuccessMessages.USER_PROFILE_LOOKUP_SUCCESS,
                UserDto.UserProfileResponse.from(user)
        ));
    }

    @PostMapping("/agree")
    public ResponseEntity<SuccessResponse<Void>> signUp(
            @CookieValue(value = "accessToken", required = false) String accessToken) {

        validateToken(accessToken);

        Long userId = jwtUtil.extractUserId(accessToken);

        userService.signUpUser(userId);

        return ResponseEntity.ok(SuccessResponse.of(SuccessMessages.SIGNUP_SUCCESS));
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자의 계정을 탈퇴처리")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "회원 탈퇴 성공"),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 토큰 또는 이미 탈퇴한 사용자"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })

    @PostMapping("/member/withdraw")
    public ResponseEntity<SuccessResponse<Void>> memberWithdraw(
            @CookieValue(value = "accessToken", required = false) String accessToken, HttpServletResponse response){

        validateToken(accessToken);
        Long userId = jwtUtil.extractUserId(accessToken);

        userService.deleteAccount(userId);
        deleteTokenCookies(response);

        return ResponseEntity.ok(SuccessResponse.of(SuccessMessages.USER_WITHDRAW_SUCCESS));
    }

    @Operation(summary = "로그아웃", description = "현재 로그인한 사용자를 로그아웃 처리(사용자의 refreshToken을 만료시키고 쿠키를 삭제한다.)")
    @PostMapping("/member/logout")
    public ResponseEntity<SuccessResponse<Void>> logout(
            @CookieValue(value = "accessToken", required = false) String accessToken,
            @RequestBody(required = false) UserDto.LogoutRequest request,
            HttpServletResponse response){

        deleteTokenCookies(response);

        return ResponseEntity.ok(SuccessResponse.of(SuccessMessages.LOGOUT_SUCCESS));
    }

    private void validateToken(String accessToken){
        if(accessToken == null || accessToken.isEmpty()){
            throw new IllegalArgumentException(ErrorMessages.AUTH_TOKEN_REQUIRED);
        }

        if(!jwtUtil.validateToken(accessToken, "ACCESS")){
            throw new IllegalArgumentException(ErrorMessages.INVALID_TOKEN);
        }
    }

    private void deleteTokenCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", "accessToken=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
        response.addHeader("Set-Cookie", "refreshToken=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
    }
}
