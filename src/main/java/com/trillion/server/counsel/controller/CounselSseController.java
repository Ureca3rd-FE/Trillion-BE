package com.trillion.server.counsel.sse;

import com.trillion.server.common.util.JwtUtil;
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
            description = """
    상담 요약의 status(PENDING → COMPLETED/FAILED)가 변경되면
    서버에서 이벤트를 push하는 SSE 스트림입니다.

    - Content-Type: text/event-stream
    - 이 API는 응답이 종료되지 않습니다.
    - Swagger UI에서는 테스트가 불가능하며,
      브라우저 또는 EventSource로만 사용해야 합니다.
    """
    )
    @GetMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(
            @CookieValue(value = "accessToken", required = false) String accessToken
    ) {
        log.info("🔔 SSE connection request received");

        // accessToken이 없으면 즉시 완료된 emitter 반환
        if (accessToken == null || accessToken.isEmpty()) {
            log.warn("⚠️ SSE connection attempt without access token");
            SseEmitter emitter = new SseEmitter(0L);
            emitter.complete();
            return emitter;
        }

        try {
            Long userId = jwtUtil.extractUserId(accessToken);
            log.info("✅ SSE connection established for user: {}", userId);
            return emitterService.connect(userId);

        } catch (Exception e) {
            log.error("❌ SSE connection error", e);
            SseEmitter emitter = new SseEmitter(0L);
            emitter.complete();
            return emitter;
        }
    }
}