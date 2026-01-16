package com.trillion.server.users.dto;

import com.trillion.server.users.entity.UserEntity;

public class UserDto{
    public record UserProfileResponse(String nickname){
        public static UserProfileResponse from(UserEntity user){
            return new UserProfileResponse(user.getNickname());
        }
    }

    public record WithdrawRequest(
            boolean agree // 탈퇴에 동의합니다.
    ){ }

    public record LogoutRequest(
            String fcmToken // 푸시 알림을 위한 FCM 토큰
    ){ }
}