package com.cvforge.application.port.in;

import com.cvforge.application.model.ResumeData;
import java.util.List;
import java.util.Optional;

/** Inbound port for resume use cases; independent of HTTP and persistence frameworks. */
public interface ResumeUseCases {
    List<ResumeData> getAllResumes(String userId);
    Optional<ResumeData> getResumeById(Long id, String userId);
    List<ResumeData> getResumesByLayout(String userId, String layout);
    List<ResumeData> searchByTitle(String userId, String title);
    ResumeData createResume(CreateResumeCommand request);
    ResumeData updateResume(Long id, UpdateResumeCommand request, String userId);
    void deleteResume(Long id, String userId);
    long countByUserId(String userId);

    record CreateResumeCommand(String userId, String title, String jsonData, String layout, String createdBy) { }
    record UpdateResumeCommand(String title, String jsonData, String layout) { }
}
