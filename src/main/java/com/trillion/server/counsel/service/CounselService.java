package com.trillion.server.counsel.service;

import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.common.exception.SuccessMessages;
import com.trillion.server.counsel.dto.CounselDto;
import com.trillion.server.counsel.entity.CounselEntity;
import com.trillion.server.counsel.entity.CounselStatus;
import com.trillion.server.counsel.repository.CounselRepository;
import com.trillion.server.users.entity.UserEntity;
import com.trillion.server.users.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.Value;
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

    @Value("${ai.server.url")
    private String aiServerUrl;

    @Transactional
    public void createCounsel(Long userId, CounselDto.CounselCreateRequest request){
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorMessages.USER_NOT_FOUND));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        LocalDate counselDate = LocalDate.parse(request.date(), formatter);

        CounselEntity counsel = CounselEntity.builder()
                .user(user)
                .counselDate(counselDate)
                .content(request.content())
                .status(CounselStatus.PENDING)
                .build();

        counselRepository.save(counsel);

        Map<String, String> aiRequest = new HashMap<>();
        aiRequest.put("content", request.content());
        aiRequest.put("date", request.date());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(aiRequest, headers);

        try{
            log.info("AI 서버 ({})로 분석 요청 전송...", aiServerUrl);

            String aiResponseJson = restTemplate.postForObject(aiServerUrl, entity, String.class);

            log.info("AI 분석 성공. DB 업데이트");
            counsel.counselAnalysis(aiResponseJson);
        }catch (Exception e){
            log.error("AI 서버 통신 실패: {}", e.getMessage());
            counsel.failAnalysis();
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
}
