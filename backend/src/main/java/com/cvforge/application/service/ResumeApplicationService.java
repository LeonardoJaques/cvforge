package com.cvforge.application.service;

import com.cvforge.application.model.ResumeData;
import com.cvforge.application.port.in.ResumeUseCases;
import com.cvforge.application.port.out.ResumeStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Application service implementing resume use cases through the persistence port. */
@Service
public class ResumeApplicationService implements ResumeUseCases {
    private final ResumeStore resumes;
    public ResumeApplicationService(ResumeStore resumes) { this.resumes = resumes; }

    @Override @Transactional(readOnly = true)
    public List<ResumeData> getAllResumes(String userId) {
        if (userId == null || userId.isBlank()) return List.of();
        return resumes.findAllByUser(userId);
    }
    @Override @Transactional(readOnly = true)
    public Optional<ResumeData> getResumeById(Long id, String userId) { return resumes.findByIdAndUser(id, userId); }
    @Override @Transactional(readOnly = true)
    public List<ResumeData> getResumesByLayout(String userId, String layout) { return resumes.findByUserAndLayout(userId, layout); }
    @Override @Transactional(readOnly = true)
    public List<ResumeData> searchByTitle(String userId, String title) {
        if (userId == null || userId.isBlank()) return List.of();
        return resumes.searchByTitle(userId, title);
    }
    @Override @Transactional
    public ResumeData createResume(CreateResumeCommand request) {
        String layout = normalizeLayout(request.layout());
        return resumes.save(new ResumeData(null, request.userId(), request.title(), request.jsonData(), layout,
                request.createdBy(), null, null, null));
    }
    @Override @Transactional
    public ResumeData updateResume(Long id, UpdateResumeCommand request, String userId) {
        ResumeData current = resumes.findByIdAndUser(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Currículo não encontrado: " + id));
        String title = request.title() == null ? current.title() : request.title();
        String json = request.jsonData() == null ? current.jsonData() : request.jsonData();
        String layout = request.layout() == null ? current.layout() : normalizeLayout(request.layout());
        return resumes.save(current.withContent(title, json, layout));
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
}
