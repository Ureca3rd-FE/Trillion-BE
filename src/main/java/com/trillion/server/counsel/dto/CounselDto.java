package com.trillion.server.counsel.dto;

import com.trillion.server.counsel.entity.CounselEntity;
import com.trillion.server.counsel.entity.CounselStatus;

import lombok.Builder;
import java.time.format.DateTimeFormatter;

public class CounselDto {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

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
                    .summaryPreview(entity.getSummaryJson())
                    .build();
        }
    }

    @Builder
    public record CounselDetailResponse(
            Long counselId,
            String title,
            String counselDate,
            CounselStatus status,
            String content,
            String summaryJson,
            String createdAt
    ){
        public static CounselDetailResponse from(CounselEntity entity){
            return CounselDetailResponse.builder()
                    .counselId(entity.getId())
                    .title(entity.getTitle())
                    .counselDate(entity.getCounselDate().format(DATE_FORMATTER))
                    .status(entity.getStatus())
                    .content(entity.getContent())
                    .summaryJson(entity.getSummaryJson())
                    .createdAt(entity.getCreatedAt().format(DATE_FORMATTER))
                    .build();
        }
    }
}
