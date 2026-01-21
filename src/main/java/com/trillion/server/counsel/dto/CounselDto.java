package com.trillion.server.counsel.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trillion.server.counsel.entity.CounselEntity;
import com.trillion.server.counsel.entity.CounselStatus;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import java.time.format.DateTimeFormatter;

public class CounselDto {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Builder
    public record CounselCreateRequest(
            @NotBlank(message = "yyyy-mm-dd")
            String date,

            @NotBlank(message = "상담 내용을 입력하세요.")
            String chat
    ) {}

    @Builder
    public record CounselListResponse(
            Long counselId,
            String title,
            String date,
            CounselStatus status,
            String summaryPreview
    ){
        public static CounselListResponse from(CounselEntity entity){
            return CounselListResponse.builder()
                    .counselId(entity.getId())
                    .title(entity.getTitle() != null ? entity.getTitle() : "제목 없음")
                    .date(entity.getCreatedAt().format(DATE_FORMATTER))
                    .status(entity.getStatus())
                    .summaryPreview(extractTitleFromJson(entity.getSummaryJson()))
                    .build();
        }
    }

    @Builder
    public record CounselDetailResponse(
            Long counselId,
            String title,
            String counselDate,
            CounselStatus status,
            String chat,

            @JsonRawValue
            String summaryJson,
            String createdAt

    ){
        public static CounselDetailResponse from(CounselEntity entity){
            return CounselDetailResponse.builder()
                    .counselId(entity.getId())
                    .title(entity.getTitle())
                    .counselDate(entity.getCounselDate() != null ? entity.getCounselDate().format(DATE_FORMATTER) : "")
                    .status(entity.getStatus())
                    .chat(entity.getChat())
                    .summaryJson(entity.getSummaryJson())
                    .createdAt(entity.getCreatedAt().format(DATE_FORMATTER))
                    .build();
        }
    }

    private static String extractTitleFromJson(String jsonString){
        if(jsonString == null || jsonString.isBlank()){
            return "요약 정보 없음";
        }
        try{
            JsonNode rootNode = objectMapper.readTree(jsonString);
            JsonNode titleNode = rootNode.path("summary").path("counsel_title");

            if (titleNode.isMissingNode()) {
                return "요약 내용 없음";
            }
            return titleNode.asText();

        } catch (Exception e) {
            return "요약 정보를 불러올 수 없음";
        }
    }
}
