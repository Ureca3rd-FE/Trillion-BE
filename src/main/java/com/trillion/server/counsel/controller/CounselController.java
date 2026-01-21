package com.trillion.server.counsel.controller;

import com.trillion.server.common.exception.SuccessResponse;
import com.trillion.server.common.util.JwtUtil;
import com.trillion.server.counsel.dto.CounselDto;
import com.trillion.server.counsel.service.CounselService;
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

    @GetMapping("/{counselId}")
    public ResponseEntity<SuccessResponse<CounselDto.CounselDetailResponse>> getCounselDetail(
            @CookieValue(value = "accessToken") String accessToken,
            @PathVariable Long counselId
    ){
        Long userId = jwtUtil.extractUserId(accessToken);
        CounselDto.CounselDetailResponse response = counselService.getCounselDetail(userId, counselId);

        return ResponseEntity.ok(SuccessResponse.of(response));
    }
}
