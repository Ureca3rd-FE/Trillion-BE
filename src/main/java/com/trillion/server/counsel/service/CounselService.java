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
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
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
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.server.url}")
    private String aiServerUrl;

    @Transactional
    public Long createCounsel(Long userId, CounselDto.CounselCreateRequest request) {
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
        return counsel.getId();
    }

        @Async
        @Transactional
        public void processAiAnalysis(Long counselId, CounselDto.CounselCreateRequest request) throws JsonProcessingException{
            CounselEntity counsel = counselRepository.findById(counselId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.COUNSEL_NOT_FOUND));

        // 1. 데이터 준비 (기존과 동일)
        Map<String, String> aiRequest = new HashMap<>();
        aiRequest.put("chat", request.chat());
        aiRequest.put("date", request.date());

        String jsonBody = objectMapper.writeValueAsString(aiRequest);

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        log.info("AI request: {} ", entity.toString());

        try{
            log.info("AI 서버 ({})로 분석 요청 전송...", aiServerUrl);
            String aiResponseJson = restTemplate.postForObject(aiServerUrl, entity, String.class);
            log.info(aiResponseJson);
            log.info("AI 분석 성공. DB 업데이트");
            counsel.completeAnalysis(aiResponseJson);
        }catch (Exception e){
            log.error("AI 서버 통신 실패: {}", e.getMessage());
            counsel.failAnalysis();
        }
    }

    @Transactional
    public CounselDto.QuestionResponse question(Long userId, Long counselId, String question){
        CounselEntity counsel = counselRepository.findById(counselId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.COUNSEL_NOT_FOUND));

        if(!counsel.getUser().getId().equals(userId)){
            throw new AccessDeniedException(ErrorMessages.FORBIDDEN);
        }

        String ai_answer = "";

        try{
            Map<String, Object> ai_request = new HashMap<>();
            ai_request.put("question", question);

            JsonNode contextNode = objectMapper.readTree(counsel.getSummaryJson());
            ai_request.put("summary", contextNode);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(ai_request, headers);

            log.info("AI 서버로 추가 질문 전송중...", ai_request);
            ai_answer = restTemplate.postForObject(aiServerUrl + "/question", entity, String.class);
            log.info("AI 수신 완료,{}", ai_answer);


            if(!ai_answer.isEmpty()){
                String cleanAnswer = ai_answer;

                if (ai_answer.startsWith("\"") && ai_answer.endsWith("\"")) {
                    cleanAnswer = ai_answer.substring(1, ai_answer.length() - 1)
                            .replace("\\\"", "\"")  // 이스케이프된 따옴표 복구
                            .replace("\\n", "\n");  // 줄바꿈 복구
                }
                ai_answer = cleanAnswer;
            }
            log.info("AI 답변 정제 완료: {}", ai_answer);


            updateSummaryJson(counsel, question, ai_answer);
        }catch (Exception e){
            log.error("실패: {}", e.getMessage());
            throw new RuntimeException(ErrorMessages.COUNSEL_QUESTION_FAIL);
        }

        return CounselDto.QuestionResponse.builder()
                .question(question)
                .answer(ai_answer)
                .build();
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

        JsonNode resultNode = rootNode.path("data");

        JsonNode summaryRaw = resultNode.path("summary");

        if(summaryRaw.isMissingNode() || !summaryRaw.isObject()){
            throw new IllegalArgumentException(ErrorMessages.COUNSEL_SUMMARY_FAIL);
        }

        ObjectNode summaryNode = (ObjectNode) summaryRaw;
        com.fasterxml.jackson.databind.node.ArrayNode array;

        if(summaryNode.has("additional_questions")){
            JsonNode existingNode = summaryNode.get("additional_questions");
            if(existingNode.isArray()){
                array = (com.fasterxml.jackson.databind.node.ArrayNode) summaryNode.get("additional_questions");
            }else{
                array = summaryNode.putArray("additional_questions");
            }
        }
        else{
            array = summaryNode.putArray("additional_questions");
        }

        ObjectNode newQa = objectMapper.createObjectNode();
        newQa.put("question", question);
        newQa.put("answer", answer);



        array.add(newQa);

        String updatedJson = objectMapper.writeValueAsString(rootNode);
        counsel.completeAnalysis(updatedJson);
        log.info("CounselEntity 요약 JSON 업데이트 완료");
    }
}