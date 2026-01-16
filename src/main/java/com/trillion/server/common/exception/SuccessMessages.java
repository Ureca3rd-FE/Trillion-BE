package com.trillion.server.common.exception;

public final class SuccessMessages {

    private SuccessMessages() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String LOGOUT_SUCCESS = "로그아웃 되었습니다.";
    public static final String TOKEN_REFRESH_SUCCESS = "토큰이 재발급되었습니다.";

    public static final String USER_PROFILE_LOOKUP_SUCCESS = "회원 정보 조회에 성공했습니다.";
    public static final String USER_WITHDRAW_SUCCESS = "회원 탈퇴가 완료되었습니다.";

    public static final String REQUEST_SUCCESS = "요청이 성공적으로 처리되었습니다.";
}