package com.cvforge.application.service;

import com.cvforge.application.model.ResumeData;
import com.cvforge.application.port.in.ResumeUseCases;
import com.cvforge.application.port.out.ResumeStore;
import com.cvforge.dto.CreateResumeRequest;
import com.cvforge.dto.ResumeResponse;
import com.cvforge.dto.UpdateResumeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Locale;

/** Application service implementing the inbound resume port. */
@Service
public class ResumeApplicationService implements ResumeUseCases {
    private static final Logger log = LoggerFactory.getLogger(ResumeApplicationService.class);
    private final ResumeStore resumes;
    public ResumeApplicationService(ResumeStore resumes) { this.resumes = resumes; }

    @Override @Transactional(readOnly = true)
    public List<ResumeResponse> getAllResumes(String userId) {
        log.debug("Buscando curriculos do usuario: {}", userId);
        if (userId == null || userId.isBlank()) return List.of();
        return resumes.findAllByUser(userId).stream().map(ResumeApplicationService::toResponse).toList();
    }
    @Override @Transactional(readOnly = true)
    public Optional<ResumeResponse> getResumeById(Long id, String userId) {
        return resumes.findByIdAndUser(id, userId).map(ResumeApplicationService::toResponse);
    }
    @Override @Transactional(readOnly = true)
    public List<ResumeResponse> getResumesByLayout(String userId, String layout) {
        return resumes.findByUserAndLayout(userId, layout).stream().map(ResumeApplicationService::toResponse).toList();
    }
    @Override @Transactional(readOnly = true)
    public List<ResumeResponse> searchByTitle(String userId, String title) {
        if (userId == null || userId.isBlank()) return List.of();
        return resumes.searchByTitle(userId, title).stream().map(ResumeApplicationService::toResponse).toList();
    }
    @Override @Transactional
    public ResumeResponse createResume(CreateResumeRequest request) {
        String layout = normalizeLayout(request.getLayout());
        ResumeData data = new ResumeData(null, request.getUserId(), request.getTitle(), request.getJsonData(), layout,
                request.getCreatedBy(), null, null, null);
        return toResponse(resumes.save(data));
    }
    @Override @Transactional
    public ResumeResponse updateResume(Long id, UpdateResumeRequest request, String userId) {
        ResumeData current = resumes.findByIdAndUser(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Currículo não encontrado: " + id));
        String title = request.getTitle() == null ? current.title() : request.getTitle();
        String json = request.getJsonData() == null ? current.jsonData() : request.getJsonData();
        String layout = request.getLayout() == null ? current.layout() : normalizeLayout(request.getLayout());
        return toResponse(resumes.save(current.withContent(title, json, layout)));
    }
    @Override @Transactional
    public void deleteResume(Long id, String userId) {
        ResumeData data = resumes.findByIdAndUser(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Currículo não encontrado: " + id));
        resumes.delete(data);
    }
    @Override @Transactional(readOnly = true)
    public long countByUserId(String userId) { return resumes.countByUser(userId); }

    private String normalizeLayout(String layout) {
        List<String> valid = List.of("ATS", "EXECUTIVO", "TERMINAL", "MODERNO", "CRIATIVO", "COMPACTO");
        String normalized = layout == null ? "" : layout.toUpperCase(Locale.ROOT);
        if (!valid.contains(normalized)) throw new IllegalArgumentException("Layout inválido: " + layout + ". Layouts válidos: " + valid);
        return normalized;
    }
    private static ResumeResponse toResponse(ResumeData data) {
        ResumeResponse response = new ResumeResponse();
        response.setId(data.id()); response.setUserId(data.userId()); response.setTitle(data.title());
        response.setJsonData(data.jsonData()); response.setLayout(data.layout()); response.setCreatedBy(data.createdBy());
        response.setCreatedAt(data.createdAt()); response.setUpdatedAt(data.updatedAt());
        return response;
    }
}
