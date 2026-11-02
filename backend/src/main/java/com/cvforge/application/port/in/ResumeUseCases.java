package com.cvforge.application.port.in;

import com.cvforge.dto.CreateResumeRequest;
import com.cvforge.dto.ResumeResponse;
import com.cvforge.dto.UpdateResumeRequest;
import java.util.List;
import java.util.Optional;

/** Inbound port for resume CRUD and search use cases. */
public interface ResumeUseCases {
    List<ResumeResponse> getAllResumes(String userId);
    Optional<ResumeResponse> getResumeById(Long id, String userId);
    List<ResumeResponse> getResumesByLayout(String userId, String layout);
    List<ResumeResponse> searchByTitle(String userId, String title);
    ResumeResponse createResume(CreateResumeRequest request);
    ResumeResponse updateResume(Long id, UpdateResumeRequest request, String userId);
    void deleteResume(Long id, String userId);
    long countByUserId(String userId);
}
