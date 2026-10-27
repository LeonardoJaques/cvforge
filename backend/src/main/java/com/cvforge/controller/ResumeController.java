package com.cvforge.controller;

import com.cvforge.dto.CreateResumeRequest;
import com.cvforge.dto.ResumeResponse;
import com.cvforge.dto.UpdateResumeRequest;
import com.cvforge.service.PdfGenerationService;
import com.cvforge.service.ResumeImportService;
import com.cvforge.service.ResumeService;
import com.cvforge.service.ProfileTextImportService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);

    private final ResumeService resumeService;
    private final PdfGenerationService pdfGenerationService;
    private final ResumeImportService resumeImportService;
    private final ProfileTextImportService profileTextImportService;

    public ResumeController(ResumeService resumeService, PdfGenerationService pdfGenerationService, ResumeImportService resumeImportService, ProfileTextImportService profileTextImportService) {
        this.resumeService = resumeService;
        this.pdfGenerationService = pdfGenerationService;
        this.resumeImportService = resumeImportService;
        this.profileTextImportService = profileTextImportService;
    }

    /** Extrai dados de um PDF textual ou normaliza um arquivo JSON, sem salvar automaticamente. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importResume(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(resumeImportService.importFile(file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/import/text")
    public ResponseEntity<?> importProfileText(@RequestBody Map<String, String> payload) {
        try {
            return ResponseEntity.ok(profileTextImportService.importText(payload.getOrDefault("text", "")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", e.getMessage()));
        }
    }

    /** Lista todos os currículos (ou os do usuário logado via header) */
    @GetMapping
    public ResponseEntity<List<ResumeResponse>> getAllResumes(
            @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        log.info("GET /api/v1/resumes - usuário: {}", userId);
        List<ResumeResponse> resumes = resumeService.getAllResumes(userId);
        return ResponseEntity.ok(resumes);
    }

    /** Retorna um currículo pelo ID */
    @GetMapping("/{id}")
    public ResponseEntity<ResumeResponse> getResumeById(
            @PathVariable Long id,
            @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        log.info("GET /api/v1/resumes/{} - usuário: {}", id, userId);
        return resumeService.getResumeById(id, userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Busca currículos por layout */
    @GetMapping("/search")
    public ResponseEntity<List<ResumeResponse>> searchByLayout(
            @RequestParam String layout,
            @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        log.info("GET /api/v1/resumes/search?layout={} - usuário: {}", layout, userId);
        List<ResumeResponse> resumes = resumeService.getResumesByLayout(userId, layout);
        return ResponseEntity.ok(resumes);
    }

    /** Busca currículos por título */
    @GetMapping("/search/title")
    public ResponseEntity<List<ResumeResponse>> searchByTitle(
            @RequestParam String title,
            @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        log.info("GET /api/v1/resumes/search/title?title={} - usuário: {}", title, userId);
        List<ResumeResponse> resumes = resumeService.searchByTitle(userId, title);
        return ResponseEntity.ok(resumes);
    }

    /** Cria um novo currículo */
    @PostMapping
    public ResponseEntity<ResumeResponse> createResume(
            @Valid @RequestBody CreateResumeRequest request,
            @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        log.info("POST /api/v1/resumes - usuário: {}", userId);
        request.setUserId(userId);
        ResumeResponse created = resumeService.createResume(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Atualiza um currículo existente */
    @PutMapping("/{id}")
    public ResponseEntity<ResumeResponse> updateResume(
            @PathVariable Long id,
            @Valid @RequestBody UpdateResumeRequest request,
            @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        log.info("PUT /api/v1/resumes/{} - usuário: {}", id, userId);
        ResumeResponse updated = resumeService.updateResume(id, request, userId);
        return ResponseEntity.ok(updated);
    }

    /** Deleta um currículo */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResume(
            @PathVariable Long id,
            @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        log.info("DELETE /api/v1/resumes/{} - usuário: {}", id, userId);
        try {
            resumeService.deleteResume(id, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Gera PDF a partir de um currículo existente */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> generatePdf(
            @PathVariable Long id,
            @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        log.info("GET /api/v1/resumes/{}/pdf - usuário: {}", id, userId);

        var resumeOptional = resumeService.getResumeById(id, userId);
        if (resumeOptional.isEmpty()) return ResponseEntity.notFound().build();
        ResumeResponse resume = resumeOptional.get();

        try {
            byte[] pdfBytes = pdfGenerationService.generatePdfFromJson(resume.getJsonData(), resume.getLayout());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=curriculo_" + id + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("Erro ao gerar PDF do currículo {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /** Gera PDF diretamente a partir de JSON e layout */
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> generatePdfFromRaw(
            @RequestBody Map<String, String> payload) {
        log.info("POST /api/v1/resumes/pdf - gerando PDF direto do JSON");

        String jsonData = payload.getOrDefault("jsonData", "{}");
        String layout = payload.getOrDefault("layout", "ATS");

        try {
            byte[] pdfBytes = pdfGenerationService.generatePdfFromJson(jsonData, layout);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=curriculo.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Erro ao gerar PDF direto: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /** Retorna contagem de currículos por usuário */
    @GetMapping("/stats/count")
    public ResponseEntity<Map<String, Object>> countByUser(
            @RequestHeader(value = "Remote-User", required = false, defaultValue = "anonimo") String userId) {
        log.info("GET /api/v1/resumes/stats/count - usuário: {}", userId);
        long count = resumeService.countByUserId(userId);
        return ResponseEntity.ok(Map.of("userId", userId, "total", count));
    }
}
