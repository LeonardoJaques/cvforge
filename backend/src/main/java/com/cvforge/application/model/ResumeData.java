package com.cvforge.application.model;

import java.time.LocalDateTime;

/** Domain representation of a resume, independent from JPA and HTTP. */
public record ResumeData(Long id, String userId, String title, String jsonData, String layout,
                         String createdBy, LocalDateTime createdAt, LocalDateTime updatedAt, Integer version) {
    public ResumeData withIdAndTimestamps(Long newId, LocalDateTime created, LocalDateTime updated, Integer newVersion) {
        return new ResumeData(newId, userId, title, jsonData, layout, createdBy, created, updated, newVersion);
    }
    public ResumeData withContent(String newTitle, String newJsonData, String newLayout) {
        return new ResumeData(id, userId, newTitle, newJsonData, newLayout, createdBy, createdAt, updatedAt, version);
    }
}
