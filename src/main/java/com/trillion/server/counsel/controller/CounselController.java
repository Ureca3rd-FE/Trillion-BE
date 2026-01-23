package com.trillion.server.counsel.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.common.exception.SuccessMessages;
import com.trillion.server.common.exception.SuccessResponse;
import com.trillion.server.common.util.JwtUtil;
import com.trillion.server.counsel.dto.CounselDto;
import com.trillion.server.counsel.service.CounselService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/counsels")
@RequiredArgsConstructor
public class CounselController {
    private final CounselService counselService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<SuccessResponse<List<CounselDto.CounselListResponse>>> getCounselList(
        @CookieValue(value = "accessToken") String accessToken){

        Long userId = jwtUtil.extractUserId(accessToken);
        List<CounselDto.CounselListResponse> responses = counselService.getCounselList(userId);

        return ResponseEntity.ok(SuccessResponse.of(responses));
    }

    @PostMapping("/write")
    public ResponseEntity<SuccessResponse<Void>> createCounsel(
            @CookieValue(value = "accessToken") String accessToken,
            @Valid @RequestBody CounselDto.CounselCreateRequest request) throws JsonProcessingException {

        if(accessToken == null || accessToken.isEmpty()){
            throw new IllegalArgumentException(ErrorMessages.AUTH_TOKEN_REQUIRED);
        }

        Long userId = jwtUtil.extractUserId(accessToken);
        counselService.createCounsel(userId, request);

        return ResponseEntity.ok(SuccessResponse.of(SuccessMessages.COUNSEL_CREATE_SUCCESS));
    }

    @GetMapping("/{counselId}")
    public ResponseEntity<SuccessResponse<CounselDto.CounselDetailResponse>> getCounselDetail(
            @CookieValue(value = "accessToken") String accessToken,
            @PathVariable Long counselId
    ){
        Long userId = jwtUtil.extractUserId(accessToken);
        CounselDto.CounselDetailResponse response = counselService.getCounselDetail(userId, counselId);

        return ResponseEntity.ok(SuccessResponse.of(response));
    }

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
