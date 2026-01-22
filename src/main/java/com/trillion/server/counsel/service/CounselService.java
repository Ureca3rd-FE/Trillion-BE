package com.trillion.server.counsel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.counsel.dto.CounselDto;
import com.trillion.server.counsel.entity.CounselEntity;
import com.trillion.server.counsel.entity.CounselStatus;
import com.trillion.server.counsel.repository.CounselRepository;
import com.trillion.server.users.entity.UserEntity;
import com.trillion.server.users.repository.UserRepository;
import io.swagger.v3.core.util.Json;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselService {
    private final CounselRepository counselRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.server.url}")
    private String aiServerUrl;

    @Transactional
    public void createCounsel(Long userId, CounselDto.CounselCreateRequest request) throws JsonProcessingException {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.USER_NOT_FOUND));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate counselDate = LocalDate.parse(request.date(), formatter);

        CounselEntity counsel = CounselEntity.builder()
                .user(user)
                .counselDate(counselDate)
                .chat(request.chat())
                .status(CounselStatus.PENDING)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        counselRepository.save(counsel);

        // 1. 데이터 준비 (기존과 동일)
        Map<String, String> aiRequest = new HashMap<>();
        aiRequest.put("chat", request.chat());
        aiRequest.put("date", request.date());

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonBody = objectMapper.writeValueAsString(aiRequest);

        RestTemplate tempRestTemplate = new RestTemplate();

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        try{
            log.info("AI 서버 ({})로 분석 요청 전송...", aiServerUrl);

            String aiResponseJson = tempRestTemplate.postForObject(aiServerUrl, entity, String.class);

            log.info("AI 분석 성공. DB 업데이트");
            counsel.completeAnalysis(aiResponseJson);
        }catch (Exception e){
            log.error("AI 서버 통신 실패: {}", e.getMessage());
            counsel.failAnalysis();
        }
    }

    @Transactional
    public void question(Long userId, Long counselId, String question){
        CounselEntity counsel = counselRepository.findById(counselId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.COUNSEL_NOT_FOUND));

        Map<String, Object> ai_request = new HashMap<>();
        ai_request.put("question", question);

        try{
            JsonNode contextNode = objectMapper.readTree(counsel.getSummaryJson());
            ai_request.put("context", contextNode);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(ai_request, headers);

            log.info("AI 서버로 추가 질문 전송중...");
//            String ai_answer = restTemplate.postForObject(aiServerUrl + "/question", entity, String.class);
            String ai_answer = "테스트 답변";
            log.info("AI 수신 완료");

            updateSummaryJson(counsel, question, ai_answer);
        }catch (Exception e){
            log.error("실패: {}", e.getMessage());
        }
    }

    public List<CounselDto.CounselListResponse> getCounselList(Long userId){
        List<CounselEntity> counsels = counselRepository.findAllByUserIdOrderByCounselDateDesc(userId);

        return counsels.stream()
                .map(CounselDto.CounselListResponse::from)
                .collect(Collectors.toList());
    }

    public CounselDto.CounselDetailResponse getCounselDetail(Long userId, Long counselId) {
        CounselEntity counsel = counselRepository.findById(counselId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.COUNSEL_NOT_FOUND));

        if (!counsel.getUser().getId().equals(userId)) {
            throw new AccessDeniedException(ErrorMessages.FORBIDDEN);
        }
        return CounselDto.CounselDetailResponse.from(counsel);
    }

    private void updateSummaryJson(CounselEntity counsel, String question, String answer) throws JsonProcessingException{
        JsonNode rootNode = objectMapper.readTree(counsel.getSummaryJson());
        JsonNode summaryRaw = rootNode.path("result").path("summary");

        ObjectNode summaryNode = (ObjectNode) summaryRaw;

        com.fasterxml.jackson.databind.node.ArrayNode array;
        if(summaryNode.has("additional_questions"))
            array = (com.fasterxml.jackson.databind.node.ArrayNode) summaryNode.get("additional_questions");
        else{
            array = summaryNode.putArray("additional_questions");
        }

        ObjectNode newQa = objectMapper.createObjectNode();
        newQa.put("question", question);
        newQa.put("answer", answer);

        array.add(newQa);

        String updatedJson = objectMapper.writeValueAsString(rootNode);
        counsel.completeAnalysis(updatedJson);
    }
}