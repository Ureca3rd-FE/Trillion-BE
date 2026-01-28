package com.trillion.server.counsel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.counsel.dto.CounselDto;
import com.trillion.server.counsel.entity.CounselCategory;
import com.trillion.server.counsel.entity.CounselEntity;
import com.trillion.server.counsel.entity.CounselStatus;
import com.trillion.server.counsel.repository.CounselRepository;
import com.trillion.server.users.entity.UserEntity;
import com.trillion.server.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CounselService {

    private final CounselRepository counselRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final RestTemplate restTemplate;
    private final CounselSseEmitterService sseEmitterService;

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
                .title(request.title())
                .chat(request.chat())
                .status(CounselStatus.PENDING)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        counselRepository.save(counsel);
        return counsel.getId();
    }

    @Transactional
    public Long retryCounsel(Long userId, Long counselId, CounselDto.CounselCreateRequest request){
        CounselEntity counsel = counselRepository.findById(counselId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.COUNSEL_NOT_FOUND));

        if(!counsel.getUser().getId().equals(userId)){
            throw new AccessDeniedException(ErrorMessages.FORBIDDEN);
        }

        switch (counsel.getStatus()){
            case PENDING:
                throw new AccessDeniedException(ErrorMessages.AI_IS_RUNNING);
            case COMPLETED:
                throw new IllegalStateException("이미 분석이 완료된 상담입니다.");
            case FAILED:

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                LocalDate counselDate = LocalDate.parse(request.date(), formatter);

                counsel.retryAnalysis(request.title(), request.chat(), counselDate);
                break;
            default:
                throw new IllegalArgumentException("알 수 없는 상담 상태입니다.");
        }

        return counsel.getId();
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long counselId) {
        if (counselId == null) return false;
        return counselRepository.existsById(counselId);
    }

    @Async
    public void processAiAnalysis(Long counselId, CounselDto.CounselCreateRequest request) {
        try {
//            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
//            factory.setConnectTimeout(5000);
//            factory.setReadTimeout(120000);
//            factory.setBufferRequestBody(true);
//
//            RestTemplate localRestTemplate = new RestTemplate(factory);
//            localRestTemplate.getMessageConverters()
//                    .add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

            Map<String, String> aiRequestMap = new HashMap<>();
            aiRequestMap.put("chat", request.chat());
            aiRequestMap.put("date", request.date());

            String jsonBody = objectMapper.writeValueAsString(aiRequestMap);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            log.info("AI 서버({})로 분석 요청 전송 (Timeout: 120s)", aiServerUrl);

            String aiResponseJson = restTemplate.postForObject(aiServerUrl, entity, String.class);
            log.info("AI 응답 수신 완료: {}", aiResponseJson);

            CounselCategory category = extractCategory(aiResponseJson);
            updateStatusInTransaction(counselId, CounselStatus.COMPLETED, aiResponseJson, category);

            log.info("AI 분석 성공 (CounselId: {})", counselId);

        } catch (RestClientResponseException e) {
            log.error("AI 서버 통신 에러 (Code: {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            updateStatusInTransaction(counselId, CounselStatus.FAILED, null, null);
        } catch (Exception e) {
            log.error("AI 분석 중 예상치 못한 에러: ", e);
            updateStatusInTransaction(counselId, CounselStatus.FAILED, null, null);
        }
    }

    public CounselDto.QuestionResponse question(Long userId, Long counselId, String question){
        CounselEntity counsel = counselRepository.findById(counselId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.COUNSEL_NOT_FOUND));

        if(!counsel.getUser().getId().equals(userId)){
            throw new AccessDeniedException(ErrorMessages.FORBIDDEN);
        }

        String aiAnswer = "";

        try{
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(60000);
            factory.setBufferRequestBody(true);

            RestTemplate localRestTemplate = new RestTemplate(factory);
            localRestTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

            Map<String, Object> aiRequestMap = new HashMap<>();
            aiRequestMap.put("question", question);
            JsonNode contextNode = objectMapper.readTree(counsel.getSummaryJson());
            aiRequestMap.put("summary", contextNode);

            String jsonBody = objectMapper.writeValueAsString(aiRequestMap);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            log.info("AI 서버로 추가 질문 전송중 (Timeout: 60s)");

            String rawResponse = localRestTemplate.postForObject(aiServerUrl + "/question", entity, String.class);

            if(rawResponse != null) {
                if (rawResponse.startsWith("\"") && rawResponse.endsWith("\"")) {
                    aiAnswer = rawResponse.substring(1, rawResponse.length() - 1)
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n");
                } else {
                    aiAnswer = rawResponse;
                }
            }
            log.info("AI 답변 수신 완료: {}", aiAnswer);
            final String finalAnswer = aiAnswer;

            transactionTemplate.execute(status -> {
                CounselEntity targetCounsel = counselRepository.findById(counselId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.COUNSEL_NOT_FOUND));
                try{
                    updateSummaryJson(targetCounsel, question, finalAnswer);
                }catch (JsonProcessingException e){
                    throw new RuntimeException("JSON 파싱 에러", e);
                }
                return null;
            });
        } catch (Exception e){
            log.error("질문 처리 실패: {}", e.getMessage());
            throw new RuntimeException(ErrorMessages.COUNSEL_QUESTION_FAIL);
        }

        return CounselDto.QuestionResponse.builder()
                .question(question)
                .answer(aiAnswer)
                .build();
    }

    @Transactional(readOnly = true)
    public CounselDto.CounselCursorResponse getCounselList(Long userId, Long cursorId, int size) {
        Pageable pageable = PageRequest.of(0, size);
        List<CounselEntity> counsels;

        if (cursorId == null) {
            counsels = counselRepository.findAllByUserIdOrderByIdDesc(userId, pageable);
        } else {
            counsels = counselRepository.findByUserIdAndIdLessThan(userId, cursorId, pageable);
        }

        Long nextCursorId = null;
        boolean hasNext = false;

        if (!counsels.isEmpty()) {
            CounselEntity lastCounsel = counsels.get(counsels.size() - 1);
            nextCursorId = lastCounsel.getId();
            hasNext = counsels.size() == size;
        }

        List<CounselDto.CounselListResponse> counselDtos = counsels.stream()
                .map(CounselDto.CounselListResponse::from)
                .collect(Collectors.toList());

        return CounselDto.CounselCursorResponse.builder()
                .content(counselDtos)
                .hasNext(hasNext)
                .nextCursorId(nextCursorId)
                .build();
    }

    @Transactional(readOnly = true)
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
        if(summaryRaw.isMissingNode()){
            summaryRaw = resultNode;
        }

        if(!summaryRaw.isObject()){
            throw new IllegalArgumentException(ErrorMessages.COUNSEL_SUMMARY_FAIL);
        }

        ObjectNode summaryNode = (ObjectNode) summaryRaw;
        ArrayNode array;

        if(summaryNode.has("additional_questions")){
            JsonNode existingNode = summaryNode.get("additional_questions");
            if(existingNode.isArray()){
                array = (ArrayNode) existingNode;
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

    private record StatusChangedEvent(Long userId, Long counselId, CounselStatus status) {}

    private void updateStatusInTransaction(Long counselId, CounselStatus nextStatus, String json, CounselCategory category) {
        StatusChangedEvent event = transactionTemplate.execute(action -> {
            CounselEntity counsel = counselRepository.findById(counselId)
                    .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.COUNSEL_NOT_FOUND));

            if (nextStatus == CounselStatus.COMPLETED) counsel.completeAnalysis(json, category);
            else counsel.failAnalysis();

            counselRepository.save(counsel);
            counselRepository.flush();

            return new StatusChangedEvent(
                    counsel.getUser().getId(),
                    counsel.getId(),
                    counsel.getStatus()
            );
        });

        if (event != null) {
            sseEmitterService.sendStatusChanged(event.userId(), event.counselId(), event.status());
        }
    }

    private CounselCategory extractCategory(String jsonString) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(jsonString);
        JsonNode dataNode = root.path("data");
        JsonNode categoryNode = dataNode.path("summary").path("category");

        if (categoryNode.isMissingNode() || categoryNode.isNull()) {
            categoryNode = dataNode.path("category");
        }

        String category = categoryNode.asText();

        if(category == null || category.isBlank()){
            log.error("JSON 구조에서 카테고리를 찾을 수 없음: {}", jsonString);
            throw new IllegalArgumentException(ErrorMessages.CATRGORY_NOT_FOUND);
        }

        return CounselCategory.from(category);
    }
}