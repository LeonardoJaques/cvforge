package com.cvforge.service;

import com.cvforge.dto.CreateResumeRequest;
import com.cvforge.dto.ResumeResponse;
import com.cvforge.dto.UpdateResumeRequest;
import com.cvforge.entity.Resume;
import com.cvforge.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
    private final ResumeRepository resumeRepository;

    public ResumeService(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> getAllResumes(String userId) {
        log.debug("Buscando currículos do usuário: {}", userId);
        if (userId == null || userId.isBlank()) return List.of();
        List<Resume> resumes = resumeRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return resumes.stream().map(ResumeResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ResumeResponse> getResumeById(Long id, String userId) {
        log.debug("Buscando currículo por ID: {} para usuário: {}", id, userId);
        return resumeRepository.findByIdAndUserId(id, userId).map(ResumeResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> getResumesByLayout(String userId, String layout) {
        log.debug("Buscando currículos layout '{}' do usuário: {}", layout, userId);
        List<Resume> resumes = resumeRepository.findByUserIdAndLayoutOrderByCreatedAtDesc(userId, layout);
        return resumes.stream().map(ResumeResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> searchByTitle(String userId, String title) {
        log.debug("Buscando currículos por título '{}' do usuário: {}", title, userId);
        if (userId == null || userId.isBlank()) return List.of();
        List<Resume> resumes = resumeRepository.findByTitleContainingIgnoreCaseAndUserId(title, userId);
        return resumes.stream().map(ResumeResponse::fromEntity).toList();
    }

    @Transactional
    public ResumeResponse createResume(CreateResumeRequest request) {
        log.info("Criando currículo: {} para usuário: {}", request.getTitle(), request.getUserId());

        request.setLayout(normalizeLayout(request.getLayout()));

        Resume resume = new Resume(
                request.getUserId(),
                request.getTitle(),
                request.getJsonData(),
                request.getLayout(),
                request.getCreatedBy()
        );

        Resume saved = resumeRepository.save(resume);
        log.info("Currículo criado com ID: {}", saved.getId());
        return ResumeResponse.fromEntity(saved);
    }

    @Transactional
    public ResumeResponse updateResume(Long id, UpdateResumeRequest request, String userId) {
        log.info("Atualizando currículo ID: {} para usuário: {}", id, userId);

        Resume resume = resumeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Currículo não encontrado: " + id));

        if (request.getTitle() != null) {
            resume.setTitle(request.getTitle());
        }
        if (request.getJsonData() != null) {
            resume.setJsonData(request.getJsonData());
        }
        if (request.getLayout() != null) {
            resume.setLayout(normalizeLayout(request.getLayout()));
        }

        Resume updated = resumeRepository.save(resume);
        log.info("Currículo atualizado: {}", updated.getId());
        return ResumeResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteResume(Long id, String userId) {
        log.info("Deletando currículo ID: {} para usuário: {}", id, userId);
        Resume resume = resumeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Currículo não encontrado: " + id));
        resumeRepository.delete(resume);
        log.info("Currículo deletado: {}", id);
    }

    private String normalizeLayout(String layout) {
        List<String> validLayouts = List.of("ATS", "EXECUTIVO", "TERMINAL", "MODERNO", "CRIATIVO", "COMPACTO");
        String normalized = layout == null ? "" : layout.toUpperCase(Locale.ROOT);
        if (!validLayouts.contains(normalized)) {
            throw new IllegalArgumentException("Layout inválido: " + layout + ". Layouts válidos: " + validLayouts);
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public long countByUserId(String userId) {
        log.debug("Contando currículos do usuário: {}", userId);
        return resumeRepository.countByUserId(userId);
    }
}
