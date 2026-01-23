package com.trillion.server.counsel.entity;

import com.trillion.server.users.entity.UserEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "counsel")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CounselEntity {

    private static final Logger log = LoggerFactory.getLogger(CounselEntity.class);
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private LocalDate counselDate;

    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String chat;

    @Column(name = "summary_json", columnDefinition = "json")
    private String summaryJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CounselStatus status;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public CounselEntity(UserEntity user, LocalDate counselDate, String chat, String title, String summaryJson, CounselStatus status, LocalDateTime createdAt) {
        this.user = user;
        this.counselDate = counselDate;
        this.chat = chat;
        this.title = title;
        this.summaryJson = summaryJson;
        this.status = status != null ? status : CounselStatus.PENDING;
        this.createdAt = createdAt;
    }

    public void completeSummary(String title, String summaryJson) {
        this.title = title;
        this.summaryJson = summaryJson;
        this.status = CounselStatus.COMPLETED;
    }
    
    public void failSummary() {
        this.status = CounselStatus.FAILED;
    }

    public void completeAnalysis(String summaryJson){
        this.summaryJson = summaryJson;
        log.info("Summary JSON set to: {}", summaryJson);
        this.status = CounselStatus.COMPLETED;
    }

    public void failAnalysis(){
        this.status = CounselStatus.FAILED;
    }
}