package com.trillion.server.counsel.controller;

import com.trillion.server.common.util.JwtUtil;
import com.trillion.server.counsel.service.CounselSseEmitterService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/counsels/sse")
@Slf4j
public class CounselSseController {

    private final CounselSseEmitterService emitterService;
    private final JwtUtil jwtUtil;

    @Operation(
            summary = "상담 요약 상태 변경 SSE 스트림",
            description = "상담 요약의 status(PENDING → COMPLETED/FAILED)가 변경되면 서버에서 이벤트를 push하는 SSE 스트림입니다."
    )
    @GetMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(@CookieValue(value = "accessToken", required = false) String accessToken) {
        // accessToken이 없으면 즉시 완료된 emitter 반환
        if (accessToken == null || accessToken.isEmpty()) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.complete();
            return emitter;
        }

        try {
            Long userId = jwtUtil.extractUserId(accessToken);
            return emitterService.connect(userId);
        } catch (Exception e) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.complete();
            return emitter;
        }
    }
}