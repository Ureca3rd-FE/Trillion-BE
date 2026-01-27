package com.trillion.server.counsel.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.common.exception.SuccessMessages;
import com.trillion.server.common.exception.SuccessResponse;
import com.trillion.server.common.util.JwtUtil;
import com.trillion.server.counsel.dto.CounselDto;
import com.trillion.server.counsel.service.CounselService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/counsels")
@RequiredArgsConstructor
@Tag(name ="상담", description = "상담 요약 관리 API")
public class CounselController {
    private final CounselService counselService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "회원 요약 리스트 조회", description = "해당 회원이 작성한 상담 요약 리스트를 조회힙니다.")
    @GetMapping
    public ResponseEntity<SuccessResponse<CounselDto.CounselCursorResponse>> getCounselList(
            @CookieValue(value = "accessToken") String accessToken,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "10") int size ){

        Long userId = jwtUtil.extractUserId(accessToken);

        CounselDto.CounselCursorResponse response = counselService.getCounselList(userId, cursorId, size);
        return ResponseEntity.ok(SuccessResponse.of(response));
    }

    @Operation(summary = "요약하기", description = "상담 요약을 생성합니다.")
    @PostMapping("/summary")
    public ResponseEntity<SuccessResponse<Void>> createCounsel(
            @CookieValue(value = "accessToken") String accessToken,
            @Valid @RequestBody CounselDto.CounselCreateRequest request) throws JsonProcessingException {

        if(accessToken == null || accessToken.isEmpty()){
            throw new IllegalArgumentException(ErrorMessages.AUTH_TOKEN_REQUIRED);
        }
        Long userId = jwtUtil.extractUserId(accessToken);
        Long counselId;

        if (request.counselId() != null && counselService.existsById(request.counselId())) {
            counselId = counselService.retryCounsel(userId, request.counselId(), request);
        } else {
            counselId = counselService.createCounsel(userId, request);
        }

        counselService.processAiAnalysis(counselId, request);

        return ResponseEntity.ok(SuccessResponse.of(SuccessMessages.COUNSEL_CREATE_SUCCESS));
    }

    @Operation(summary = "요약 상세 조회", description = "상담 요약의 상세 내용을 조회합니다.")
    @GetMapping("/{counselId}")
    public ResponseEntity<SuccessResponse<CounselDto.CounselDetailResponse>> getCounselDetail(
            @CookieValue(value = "accessToken") String accessToken,
            @PathVariable Long counselId
    ){
        Long userId = jwtUtil.extractUserId(accessToken);
        CounselDto.CounselDetailResponse response = counselService.getCounselDetail(userId, counselId);

        return ResponseEntity.ok(SuccessResponse.of(response));
    }

    @Operation(summary = "추가 질문", description = "상세 조회에서 추가질문을 하고 답변을 받습니다.")
    @PostMapping("/{counselId}/question")
    public ResponseEntity<SuccessResponse<CounselDto.QuestionResponse>> question(
            @CookieValue(value = "accessToken") String accessToken,
            @PathVariable Long counselId,
            @Valid @RequestBody CounselDto.QuestionRequest request
    ){
        Long userId = jwtUtil.extractUserId(accessToken);

        CounselDto.QuestionResponse responseData = counselService.question(userId, counselId, request.question());

        return ResponseEntity.ok(SuccessResponse.of(SuccessMessages.COUNSEL_QUESTION_SUCCESS, responseData));
    }
}
