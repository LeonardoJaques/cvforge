package com.cvforge.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_comparisons")
public class JobComparison {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 50)
    private String userId;
    private Long resumeId;
    @Column(nullable = false, length = 100)
    private String profileLabel;
    @Column(nullable = false, columnDefinition = "text")
    private String jobText;
    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String resultJson;
    @Column(nullable = false, length = 2)
    private String language;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    public JobComparison() { }
    public JobComparison(String userId, Long resumeId, String profileLabel, String jobText, String resultJson, String language) {
        this.userId = userId; this.resumeId = resumeId; this.profileLabel = profileLabel;
        this.jobText = jobText; this.resultJson = resultJson; this.language = language;
        this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public Long getResumeId() { return resumeId; }
    public String getProfileLabel() { return profileLabel; }
    public String getJobText() { return jobText; }
    public String getResultJson() { return resultJson; }
    public String getLanguage() { return language; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
