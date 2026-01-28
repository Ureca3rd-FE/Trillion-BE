package com.trillion.server.counsel.sse;

import com.trillion.server.counsel.entity.CounselStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class CounselSseEmitterService {

    private static final long TIMEOUT = 60L * 60 * 1000; // 1시간
    private final Map<Long, List<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);

        emittersByUserId
                .computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>())
                .add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));

        // (선택) 연결 직후 더미 이벤트 한번 쏘면 프록시/브라우저에서 연결 안정적일 때가 많음
        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("ok"));
        } catch (Exception e) {
            remove(userId, emitter);
        }

        return emitter;
    }

    public void sendStatusChanged(Long userId, Long counselId, CounselStatus status) {
        List<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null || emitters.isEmpty()) return;

        Map<String, Object> payload = Map.of(
                "counselId", counselId,
                "status", status.name()
        );

        List<SseEmitter> dead = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("COUNSEL_STATUS_CHANGED")
                        .data(payload)
                );
            } catch (Exception e) {
                dead.add(emitter);
            }
        }

        emitters.removeAll(dead);
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emittersByUserId.get(userId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) emittersByUserId.remove(userId);
    }
}
