package com.trillion.server.common.exception;

public final class ErrorMessages {
    
    private ErrorMessages() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    public static final String AUTH_TOKEN_REQUIRED = "인증 토큰이 필요합니다.";
    public static final String INVALID_TOKEN = "유효하지 않은 토큰입니다.";
    public static final String INVALID_REFRESH_TOKEN = "유효하지 않은 리프레시 토큰입니다.";
    public static final String REFRESH_TOKEN_REQUIRED = "리프레시 토큰이 필요합니다.";
    
    public static final String USER_ID_REQUIRED = "사용자 ID가 필요합니다.";
    public static final String USER_NOT_FOUND = "사용자를 찾을 수 없습니다.";
    public static final String USER_ALREADY_DELETED = "이미 탈퇴한 사용자입니다.";
    
    public static final String VALIDATION_FAILED = "입력값 검증에 실패했습니다.";
    public static final String INTERNAL_SERVER_ERROR = "서버 오류가 발생했습니다.";
    public static final String LOGIN_FAILED = "로그인에 실패했습니다.";
    
    public static final String BAD_REQUEST = "잘못된 요청입니다.";
    public static final String NOT_FOUND = "요청한 리소스를 찾을 수 없습니다.";
    public static final String UNAUTHORIZED = "인증이 필요합니다.";
    public static final String FORBIDDEN = "접근 권한이 없습니다.";

    public static final String COUNSEL_NOT_FOUND = "상담 기록을 찾을 수 없습니다.";

    public static final String AI_ANALYSIS_FAILED = "AI 분석 중 오류가 발생했습니다.";
    public static final String INVALID_DATE_FORMAT = "날짜 형식이 올바르지 않습니다. (yyyy.MM.dd)";

    public static final String COUNSEL_QUESTION_FAIL = "AI 질문 처리에 실패";
    public static final String CATRGORY_NOT_FOUND = "카테고리가 없습니다.";
    public static final String COUNSEL_SUMMARY_FAIL = "JSON 구조가 올바르지 않습니다.";


    public static final String AI_IS_RUNNING = "현재 AI 분석이 진행중입니다.";
}
